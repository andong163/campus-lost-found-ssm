# 杨程模块实现说明（review / claim / audit）

负责人：杨程　分支：`feature/yangcheng-review-claim`　版本：2026-11-17（R4 收尾）

本文记录 YC R1～R4 在当前 `develop` 基线（已含五人第三轮总合并 `fba0bb6`）上的实际落地范围、接口、状态规则、事务与审计证据。只描述本仓库已经存在并可运行的内容；分类、信息 CRUD、公开发布和批量接口由孙睿、袁运轩负责，本模块通过其正式契约接入。

## 一、交付范围

| 轮次 | 本仓库已实现 | 尚未由本模块提供 |
|---|---|---|
| YC R1 | 结构化审计写入 `AuditService`/`AuditTemplate`、授权日志分页、审核记录 Mapper、认领查询/关闭契约 | 依赖郭金滔的真实登录会话；当前由 `CurrentUserContext` 接入 |
| YC R2 | `ItemWorkflowService` 提审/撤回/转草稿/发布/下架/删除校验，审核决定、审核记录、待办/历史/记录接口 | 提审/发布等 HTTP 入口按分工属于袁运轩的 article Controller；本模块交付 Service 与测试 |
| YC R3 | 认领申请、我的认领、申请队列、物品申请列表、批准/驳回、撤销、FOUND 联系方式策略、并发批准 | 公开物品列表/详情与“联系失主”聚合入口由 article 模块接入 |
| YC R4 | FOUND 发布者确认交接、LOST 作者确认找回、四类数据同事务、其他待申请关闭、日志查询页、异常矩阵 | 外部 Tomcat/真实 MySQL 的最终部署复验由高鸣远汇总 |

页面为模块独立静态页，使用同源 `fetch` 与会话 Cookie；公共布局和导航由高鸣远统一改造后再挂载，当前不修改公共首页。

## 二、文件地图

### 2.1 归属本模块

```
src/main/java/com/team/cms/review/
├── ReviewPermissions.java
├── controller/ReviewController.java
├── domain/ReviewDecision.java
├── dto/{ReviewDecisionRequest,ReviewHistoryQuery,ReviewRecordView,ReviewTaskQuery,ReviewTaskView}.java
├── entity/ReviewRecord.java
├── mapper/{ReviewRecordMapper,ReviewTaskMapper}.java
└── service/{ItemWorkflowService,ItemWorkflowServiceImpl,ReviewService,ReviewServiceImpl}.java

src/main/java/com/team/cms/claim/
├── ClaimPermissions.java
├── controller/ClaimController.java
├── domain/ClaimDecision.java
├── dto/{ClaimCancelRequest,ClaimCreateRequest,ClaimDecisionRequest,ClaimQuery,ClaimView,
│        FoundContactView,HandoverView,ResolveRequest}.java
├── entity/ClaimRequest.java
├── mapper/ClaimRequestMapper.java
└── service/{ClaimReadPolicy,ClaimReadPolicyImpl,ClaimService,ClaimServiceImpl}.java

src/main/java/com/team/cms/audit/
├── controller/OperationLogController.java
├── dto/{OperationLogQuery,OperationLogView}.java
├── entity/OperationLog.java
├── mapper/OperationLogMapper.java
└── service/{AuditEntry,AuditService,AuditTemplate,AuditWriter}.java
```

资源与页面：

```
src/main/resources/mapper/review/{ReviewTaskMapper,ReviewRecordMapper}.xml
src/main/resources/mapper/claim/ClaimRequestMapper.xml
src/main/webapp/review/review.html
src/main/webapp/claim/{manage,mine,handover}.html
src/main/webapp/audit/logs.html
src/main/webapp/yangcheng/{common.css,common.js}
```

### 2.2 依赖契约（已由 develop 正式实现）

| 文件 | 归属 | 当前作用 | 约束 |
|---|---|---|---|
| `article/entity/Article.java` | 袁运轩 | 审核/认领读取 `cms_article`；`status`、`itemType`、`resolutionStatus` 均为枚举 | 调用方使用枚举比较，不再写字符串字面量 |
| `article/mapper/ArticleMapper.java` + XML | 袁运轩 | `selectByIdForUpdate`、`updateStatusIfVersion`、`resolve` 等锁定与条件更新 | 不得改成无锁更新；审核/认领依赖 `FOR UPDATE` 串行化 |
| `category/entity/Category.java` | 孙睿 | 分类祖先可用性判定 | 与孙睿正式分类模块共用 |
| `category/mapper/CategoryMapper.java` | 孙睿 | 未删除分类读取 | 由分类模块维护 |
| `category/service/CategoryAccessService.java` | 孙睿 | 全组统一分类可用性契约 | 审核/认领只调用该接口，不另写祖先判断 |

第三轮总合并后，杨程早期的临时 `CategoryAccessServiceImpl` 已删除，`CategoryAccessService` 由孙睿的正式实现提供；`Article` 的字符串状态字段也已统一为枚举，本模块已同步适配。

## 三、HTTP 接口

基础地址 `/cms/api`，响应统一 `{code,message,data}`。除标注“公开”外均需同源会话；权限由 `@RequirePermission` + Service 所有权双重校验。

所有 POST 请求还要求 CSRF 防护：登录时服务端签发令牌，前端通过 `GET /api/auth/csrf` 读取，并在写请求头带 `X-CSRF-Token`。缺令牌或令牌不匹配统一返回 403；页面统一使用 `yangcheng/common.js` 的 `YC.post` 自动完成。

| 方法 | 路径 | 权限/所有权 | 说明 |
|---|---|---|---|
| GET | `/reviews` | `review:handle` | 审核待办；自动排除本人内容；支持 keyword/itemType/categoryId/page/size |
| GET | `/reviews/history` | `review:handle` | 审核历史；支持 articleId/decision/page/size |
| POST | `/reviews/{itemId}/decision` | `review:handle` | body `{version,decision,comment}`；驳回必须填意见 |
| GET | `/items/{itemId}/review-records` | 作者或 `review:handle` | 单物品审核记录，按时间正序 |
| POST | `/items/{itemId}/claims` | 登录学生 | body `{claimDescription,contactInfo}`；仅他人已发布 OPEN 的 FOUND |
| GET | `/claims/mine` | 登录学生 | 本人申请；支持 status/page/size |
| GET | `/claims` | `claim:handle` | 审核处理队列；支持 status/itemId/page/size |
| GET | `/items/{itemId}/claims` | 发布者或 `claim:handle` | 某物品申请列表，含证据与联系方式（仅授权端） |
| POST | `/claims/{claimId}/decision` | `claim:handle` | body `{version,decision,comment}`；批准不结案 |
| POST | `/claims/{claimId}/cancel` | 申请人 | body `{version,reason}`；待核实/已通过可撤销 |
| GET | `/items/{itemId}/handover` | 发布者或 `claim:handle` | 交接上下文：物品版本、已通过申请、申请人昵称；不含联系方式 |
| POST | `/items/{itemId}/resolve` | LOST 作者 / FOUND 发布者 | body `{version,claimId?,reason?}`；FOUND 必须有 APPROVED 申请 |
| GET | `/items/{itemId}/found-contact` | `ClaimReadPolicy` | 发布者、已通过申请人、管理员、认领/审核人员可读；每次读取写审计 |
| GET | `/operation-logs` | `audit:view` | 日志分页；operatorId/operatorName/action/result/from/to |

错误码：400 参数、401 未登录、403 越权/自审/无权读私密字段、404 不存在或未公开、409 状态/版本/重复申请冲突、500 系统错误。

## 四、状态规则

### 4.1 内容状态（`ItemWorkflowService`）

| 当前 | 动作 | 目标 | 条件 |
|---|---|---|---|
| DRAFT / REJECTED | 作者提审 | PENDING_REVIEW | 内容完整、分类及祖先有效、作者本人 |
| PENDING_REVIEW | 作者撤回 | DRAFT | 尚未审核 |
| PENDING_REVIEW | 审核员批准/驳回 | APPROVED / REJECTED | 非本人内容、version 匹配；驳回填意见 |
| APPROVED / OFFLINE | 授权人员发布 | PUBLISHED | 分类有效、未结案、无已通过认领；写 `published_at` |
| PUBLISHED | 作者或发布权限人员下架 | OFFLINE | 无已通过认领；同事务关闭 PENDING 申请 |
| APPROVED / OFFLINE | 作者改稿 | DRAFT | 无未完成申请 |
| 任意（删除前） | 删除校验 | — | 非 PENDING_REVIEW，且无 PENDING/APPROVED 申请 |

审核批准、公开发布、认领核实、实际交接是四个独立动作；批准认领不会自动把物品标成已归还。

### 4.2 认领状态（`ClaimServiceImpl`）

| 当前 | 动作 | 目标 | 条件 |
|---|---|---|---|
| — | 学生申请 | PENDING | 他人已发布 OPEN 的 FOUND、分类可绑定、无同人同物有效申请、无已通过申请 |
| PENDING | 审核员批准/驳回 | APPROVED / REJECTED | 非申请人、非发布者；批准要求物品仍 PUBLISHED+OPEN；驳回填理由 |
| PENDING / APPROVED | 申请人撤销 | CANCELLED | 本人、version 匹配；物品已结案时拒绝 |
| APPROVED | 发布者确认交接 | COMPLETED | 物品 FOUND+PUBLISHED+OPEN；同事务物品 RESOLVED、写 `resolved_at`、关闭其他 PENDING |

数据库两个生成列唯一索引是最后防线：

- `(article_id, active_applicant_id)`：同人同物最多一条 PENDING/APPROVED；
- `approved_article_id`：单物品最多一条 APPROVED。

## 五、并发、事务与审计

1. **锁顺序固定**：所有会改认领状态的动作先 `SELECT ... FOR UPDATE` 锁 `cms_article`，再锁 `cms_claim_request`。批准、撤销、交接共用同一顺序，避免死锁。
2. **条件更新**：状态更新 SQL 同时带 `id + 当前状态 + version`，影响 0 行转 409。客户端不能直接指定任意目标状态，也不能用旧版本覆盖。
3. **同一事务**：审核“状态更新 + `cms_review_record` 插入”；交接“申请 COMPLETED + 物品 RESOLVED + `resolved_at` + 关闭其他 PENDING”都在公开 Service 代理的事务中完成。内部方法不互相 `this` 调用，避免事务失效。
4. **审计隔离**：`AuditService.write` 使用 `REQUIRES_NEW`，`AuditTemplate` 捕获审计写入异常并只记录不含参数的告警，因此“日志写失败”不会把已成功的审核/认领变成 500，也不会诱导前端重复提交。审计字段只有操作者、模块、动作、目标、结果、耗时、requestId；不写正文、联系方式、密码或证据。
5. **联系方式**：公开列表/详情不带 `contact_info`；单条 `found-contact` 走 `ClaimReadPolicy`，每次成功/失败读取都写一条 `FOUND_CONTACT_READ` 审计。
6. **分类停用边界**：批准已有申请不检查分类（旧申请可继续核实）；新建申请和提审/发布必须检查分类及全部祖先。停用后新增申请返回 409。

## 六、测试与证据

R4 收尾时在当前基线执行完整 `mvn verify`：Surefire **169 项**、Failsafe `WarDeploymentIT` **1 项**全部通过。杨程模块 9 个测试类共 54 项，其中 R4 重点回归 37 项（含 HTTP 契约与交接/登录态/CSRF 适配）。明细见 [yangcheng-test-record.md](yangcheng-test-record.md)。

| 测试类 | 覆盖 |
|---|---|
| `AuditTemplateTest` | requestId、成功/失败记录、审计写入失败不影响业务 |
| `AuditServiceTest` | 真实 H2 写入、过滤、排序、超长时间范围与字段截断 |
| `ItemWorkflowTest` | 提审/撤回/发布/下架、自审拒绝、版本冲突、已通过认领阻断、LOST/FOUND 结案 |
| `ClaimFlowTest` | 申请条件、重复申请、批准互斥、撤销释放名额、联系方式策略、物品申请列表权限 |
| `ConcurrentClaimApprovalTest` | 两线程并发批准，恰好一个成功，数据库仅一条 APPROVED |
| `ReviewRollbackTest` | 审核记录插入失败时内容状态回滚 |
| `HandoverRollbackTest` | 物品结案失败时申请 COMPLETED 回滚 |
| `ReviewWorkflowTest` | 审核待办/历史/记录、批准驳回、自审回避与权限 |
| `ReviewClaimApiTest` | 401/403、CSRF、HTTP 主流程、审计 requestId、私密字段不泄露、参数校验 |

复现命令（本机无 JDK 11 时的等价运行方式）：

```powershell
$env:JAVA_HOME='C:\path\to\jdk11'
./scripts/build.ps1
```

当前机器仅安装 JDK 17，使用 `--release 11` 编译并追加 `--add-opens=java.base/java.lang=ALL-UNNAMED` 运行基线容器测试，结果见测试记录。交付前应由高鸣远在 JDK 11 执行 `scripts/build.ps1` 复跑。

## 七、已知限制与交接事项

1. **HTTP 状态入口不在本模块**：`submit/withdraw/edit-draft/offline` 及批量发布按分工由袁运轩的 article Controller 调用 `ItemWorkflowService`；本仓库只维护 Service 和本模块测试，不重复定义第二套状态 API。
2. **依赖契约已正式化**：`Article` 的状态字段已是枚举，`CategoryAccessService` 由孙睿的正式实现提供；后续若袁/孙调整字段，本模块只需同步枚举类型和 Mapper 签名，不得绕过 `FOR UPDATE` 与条件更新语义。
3. **页面未套公共布局**：当前是模块独立页，登录页和公共导航由郭金滔/高鸣远提供；页面未做浏览器截图验收，只做了静态结构与接口契约测试。为让 `DispatcherServlet`（映射 `/`）下的模块静态页可访问，`SpringMvcConfig` 保留了 `/review/**`、`/claim/**`、`/audit/**`、`/yangcheng/**` 的资源映射；需高鸣远合并时确认是否改为公共静态资源策略。
4. **真实 MySQL 并发**：本模块并发测试在 H2 MySQL 模式执行；生产唯一索引已在基线真实 MySQL 验证过，但 Service 级并发仍需高鸣远在专用 MySQL 库复跑一次并留证据。
5. **SQL 变更**：未新增表或字段，复用 `init.sql` 的 `cms_review_record`、`cms_claim_request`、`sys_operation_log` 及 003 权限种子。若无必要不新增迁移；如需优化待办查询，建议由高鸣远评估 `cms_article(status, updated_at, id)` 索引后统一编号。

## 八、合并检查清单

- [x] 分支已同步最新 `develop`，`mvn verify` 通过（169 Surefire + 1 Failsafe）。
- [x] `ArticleMapper.selectByIdForUpdate`、`updateStatus`、`resolve` 未被替换成无锁版本。
- [x] `CategoryAccessService` 由孙睿的唯一正式实现提供，审核/认领没有另写祖先判断。
- [x] 权限种子含 `review:handle`、`claim:handle`、`claim:resolve`、`audit:view`；普通学生含 `claim:create`、`claim:cancel:own`、`claim:handover:own`。
- [ ] 页面入口已挂到公共导航；登录态失效时统一提示 401。
- [ ] 真实 MySQL 复跑并发批准与交接回滚，记录环境、SQL 和结果。
- [ ] PR 正文登记 YC R1～R4 的实际提交、测试数量、未完成项和依赖人。
