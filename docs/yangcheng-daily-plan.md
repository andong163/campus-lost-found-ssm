# 杨程每日开发计划（YC R1～R4）

适用分支：`feature/yangcheng-review-claim`　计划周期：2026-10-26（周一）～2026-11-20（周五），共 20 个工作日。若需要从 2026-10-23（周五）立即开始，可把第 1 天移到 10-23，后续整体前移一个工作日，总时长不变。

本计划按“已经实现的 review / claim / audit 功能”反推每天的工作顺序：先冻结依赖契约和测试夹具，再做审计，再做状态机、审核、认领、交接和页面，最后补证据与合并。每天结束必须满足：代码可编译、当天新增测试通过、提交说明能说清“完成了什么”，不把下一轮未验收的大功能塞进当天提交。

## 一、每天固定动作

| 时点 | 动作 | 产出 |
|---|---|---|
| 上班前 15 分钟 | `git fetch origin`，把最新 `develop` 合入本人分支，看依赖人是否已合并 | 冲突清单/依赖状态一句话 |
| 上午 | 先写当天要验证的失败用例或验收点，再写实现 | 一个可运行的测试或页面交互 |
| 下午 | 补异常路径、并发/事务边界，跑本模块测试 | 通过记录 + 实际错误 |
| 下班前 20 分钟 | `git status`、`git diff --cached --stat`，只暂存本人模块文件 | 1～3 个中文提交 |
| 下班前 5 分钟 | 在 PR 草稿或日报记录：完成、阻塞、明天第一件事 | 可追踪的进度 |

提交只包含本人模块（`review`、`claim`、`audit`、对应 XML/页面/测试/文档）；`config`、`common`、`pom.xml`、公共布局、SQL 总稿不改，需要变更先找高鸣远。

## 二、20 天排期

### 第 1 周：YC R1 审计基础与状态契约

| 天 | 日期 | 当天任务 | 可交付与自测 | 建议提交说明 |
|---:|---|---|---|---|
| 1 | 10-26 周一 | 从最新 `develop` 建/同步分支；核对 `cms_article`、`cms_review_record`、`cms_claim_request`、`sys_operation_log` 字段；与袁运轩确认 `ArticleMapper` 锁定查询/条件更新签名，与孙睿确认 `CategoryAccessService`；确定 H2 夹具方案 | 分支可构建；`docs/module-contracts.md` 中依赖点有结论；`review-claim-schema.sql` 能建表 | `确认审核认领依赖契约与测试数据库结构` |
| 2 | 10-27 周二 | 写 `OperationLog` 实体/Mapper、`OperationLogQuery/View`；按 003 权限种子核对 `audit:view`；先写日志分页的失败用例 | `AuditServiceTest` 的查询/排序/时间范围用例先红后绿 | `增加操作日志数据访问与分页查询条件` |
| 3 | 10-28 周三 | 写 `AuditWriter/AuditService/AuditTemplate`；接入 `MDC requestId`；处理审计写入失败不影响业务；补单元测试 | `AuditTemplateTest` 4 项通过；`AuditServiceTest` 写入/过滤通过 | `实现结构化审计写入与失败隔离` |
| 4 | 10-29 周四 | 写 `ReviewRecord` 实体/Mapper/XML；写认领查询与关闭契约（`countActive`、`countApproved`、`closePending`、`selectApprovedForUpdate`）；补 H2 生成列与唯一索引夹具 | 审核记录可按物品查询；生成列唯一约束在 H2 生效；`ItemWorkflowTest` 能加载上下文 | `增加审核记录与认领查询关闭契约` |
| 5 | 10-30 周五 | 跑 R1 全量回归；补 `docs/api-yangcheng.http` 的日志查询片段；整理 PR YC R1（完成/依赖/测试/未完成） | 全量测试通过；PR 描述含 `AuditServiceTest`、`AuditTemplateTest` 结果；分支已同步 develop | `完成 YC R1 审计基础与状态契约` |

**R1 周末验收**：授权账号能查询日志；普通学生 403；日志有 requestId，无正文/联系方式；审核记录表可写入；与袁/孙的契约没有硬编码用户或空工作流。

### 第 2 周：YC R2 内容状态与审核工作台

| 天 | 日期 | 当天任务 | 可交付与自测 | 建议提交说明 |
|---:|---|---|---|---|
| 6 | 11-02 周一 | 写 `ItemWorkflowService` 接口与实现：`submit/withdraw/toDraft`；实现所有权、状态、version 校验；内容完整性校验；分类可绑定校验 | `ItemWorkflowTest` 的提审/撤回/停用分类用例通过；DRAFT→PENDING_REVIEW→DRAFT 版本各 +1 | `实现提审撤回与改稿状态命令` |
| 7 | 11-03 周二 | 补 `publish/offline/assertDeletable`；已通过认领阻断下架/改稿/删除；下架同事务关闭 PENDING 申请 | 下架后申请 CANCELLED 且有原因；有 APPROVED 时下架/删除返回 409 | `实现发布下架与删除前状态校验` |
| 8 | 11-04 周三 | 写 `ReviewService.decide`：锁物品、自审拒绝、version 校验、条件更新、审核记录同事务；驳回必须填意见 | 审核批准/驳回/自审/旧版本用例通过；`ReviewRollbackTest` 证明记录失败时状态回滚 | `实现审核决定与审核记录同事务` |
| 9 | 11-05 周四 | 写待办/历史/单物品记录查询；待办排除本人内容；写 `ReviewController` 与审核工作台页面 | `GET /reviews` 只返回他人 PENDING_REVIEW；页面可批准/驳回并刷新；401/403 提示正确 | `增加审核待办历史接口与工作台页面` |
| 10 | 11-06 周五 | 跑 R2 回归；补审核接口片段和页面操作说明；发起 YC R2 PR；与袁运轩对齐提审/发布调用点 | 全量测试通过；PR 说明袁的 article Controller 应调用哪些 Service 方法 | `完成 YC R2 内容状态与审核工作台` |

**R2 周末验收**：学生提审→审核员驳回→学生修改重提→审核员批准→授权发布→下架可连贯执行；非法状态、旧版本、自审全部拒绝；状态和审核记录不出现一半成功。

### 第 3 周：YC R3 认领申请与核实

| 天 | 日期 | 当天任务 | 可交付与自测 | 建议提交说明 |
|---:|---|---|---|---|
| 11 | 11-09 周一 | 写 `ClaimRequest` 实体/Mapper/XML；确认两个生成列不进入实体；写物品行锁与申请行锁顺序；补 `countActive/countApproved` 查询 | 申请、批准、撤销 SQL 均带状态与 version 条件；H2/MySQL 生成列唯一索引可用 | `增加认领申请数据访问与行锁契约` |
| 12 | 11-10 周二 | 写 `ClaimService.apply/cancel`：仅他人已发布 OPEN 的 FOUND；分类可绑定；同人同物唯一；撤销释放名额；撤销原因必填 | 申请条件、重复申请、撤销后重申请、停用分类拒绝用例通过 | `实现认领申请与撤销` |
| 13 | 11-11 周三 | 写 `ClaimService.decide`：审核员权限、申请人/发布者回避、批准要求 PUBLISHED+OPEN、唯一 APPROVED；写并发批准测试 | `ConcurrentClaimApprovalTest` 两线程恰好一个成功；批准后物品仍 OPEN | `实现认领核实与并发批准保护` |
| 14 | 11-12 周四 | 写我的认领、处理队列、物品申请列表、交接上下文；写 `ClaimReadPolicy`；写认领处理/我的认领页面 | 未通过申请人读 FOUND 联系方式 403；已通过/作者/审核人员可读；列表不含公开 DTO | `增加认领列表联系方式策略与页面` |
| 15 | 11-13 周五 | 跑 R3 回归；检查公开响应无私密字段；补接口片段；发起 YC R3 PR | 全量测试通过；页面可申请、撤销、批准/驳回；PR 含并发与隐私证据 | `完成 YC R3 认领申请与核实` |

**R3 周末验收**：公开招领→学生申请→审核员核实通过→双方可读联系方式；重复申请、第二个批准、本人申请/本人发布物品核实全部拒绝。

### 第 4 周：YC R4 交接结案与业务审计闭环

| 天 | 日期 | 当天任务 | 可交付与自测 | 建议提交说明 |
|---:|---|---|---|---|
| 16 | 11-16 周一 | 在 `ItemWorkflowService.resolve` 实现 FOUND 交接：申请 COMPLETED、物品 RESOLVED、`resolved_at`、关闭其他 PENDING；实现 LOST 作者确认找回；重复/非本人拒绝 | `ItemWorkflowTest` 交接四项更新通过；`HandoverRollbackTest` 结案失败时申请回滚 | `实现招领交接与寻物找回结案` |
| 17 | 11-17 周二 | 写交接入口页、日志查询页；补 `FOUND_CONTACT_READ` 审计；检查日志只含安全字段；补 401/403/409 页面提示 | 交接页显示版本/已通过申请人；日志页可按操作者/动作/结果/时间筛选；读取联系方式成功与失败都有日志 | `增加交接入口日志页面与联系方式审计` |
| 18 | 11-18 周三 | 跑异常矩阵：自审、越权、旧版本、重复交接、已结案新申请、分类停用旧申请、并发批准、审计写入失败；跑全量回归 | 75 项测试通过；失败场景没有部分成功；记录实际错误与修复 | `补充审核认领异常矩阵与回归修复` |
| 19 | 11-19 周四 | 写 `yangcheng-implementation.md`、`yangcheng-test-record.md`、接口片段；整理 AI 辅助记录与人工修改；自查命名/注释/日志脱敏 | 文档与实际代码一致；PR 正文可直接引用测试数量和限制 | `补充杨程模块实现说明测试记录与接口片段` |
| 20 | 11-20 周五 | 发起 YC R4 PR；与高鸣远做合并前演示：审核→申请→核实→交接→日志；同步 develop 后复跑；登记 PR/合并提交/验收结果 | PR 合并；演示脚本和结果留档；分支保留到全组验收结束 | `完成 YC R4 交接结案与业务审计闭环` |

## 三、依赖与停合条件

| 依赖 | 最晚需要时间 | 未到位时怎么办 | 停合条件 |
|---|---|---|---|
| GJT R1 真实登录/权限 | 第 1 天前 | 测试用 `CurrentUserContext` 注入，生产代码不写死用户 | 用固定用户 ID 或临时放行代替鉴权 |
| `CategoryAccessService`（SR R1） | 第 1 周内 | 保留契约和最小实现，接口不变 | 审核/认领另写一套祖先判断 |
| `ArticleMapper` 锁定查询（YYX R1） | 第 2 周前 | 保留本模块最小契约，锁定/条件更新签名不变 | 改成无锁更新或前端直接改状态 |
| article 提审/发布 HTTP Controller | 第 2 周末 | 本模块先交 Service + 测试，不重复定义第二套 API | 为演示在审核模块另写发布接口 |
| 公共布局/登录页（GMY/GJT） | 第 3 周前 | 页面保持独立可访问，不修改公共首页 | 把模块页面塞进公共首页造成冲突 |
| 外部 Tomcat/真实 MySQL | 第 4 周 | 先交 H2 + WAR 构建证据，标注待复验 | 把 H2 结果说成生产并发已验证 |

## 四、每日验收命令

在 `D:\JAVAEE\campus-lost-found-ssm` 下执行（本机 JDK 版本按实际替换）：

```powershell
# 只跑本模块
mvn -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -B -ntp `
  -DargLine="--add-opens=java.base/java.lang=ALL-UNNAMED" `
  -Dtest="AuditTemplateTest,AuditServiceTest,ItemWorkflowTest,ClaimFlowTest,ConcurrentClaimApprovalTest,ReviewRollbackTest,HandoverRollbackTest,ReviewClaimApiTest" test

# 全量
mvn -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -B -ntp `
  -DargLine="--add-opens=java.base/java.lang=ALL-UNNAMED" clean test

# 交付构建（JDK 11）
./scripts/build.ps1
```

每天至少保留一份 Surefire 报告或测试输出；失败用例写明“现象、原因、修复、复测”，不要只写“已修复”。

## 五、如果周期被压缩

课程截止更早时，按 10 天压缩，把每轮压成 2 天，但仍保留顺序：

| 天 | 内容 |
|---:|---|
| 1 | 契约 + 审计实体/Mapper/Service |
| 2 | 审计测试 + 审核记录/认领契约 + R1 PR |
| 3 | 提审/撤回/改稿 + 发布/下架/删除校验 |
| 4 | 审核决定 + 待办/历史 + 工作台页面 + R2 PR |
| 5 | 认领实体/Mapper + 申请/撤销 |
| 6 | 核实/并发 + 列表/联系方式 + 页面 |
| 7 | R3 回归 + PR |
| 8 | 交接/寻物结案 + 回滚测试 |
| 9 | 日志/交接页面 + 异常矩阵 + 全量 |
| 10 | 文档 + YC R4 PR + 演示 |

不能压缩的部分：并发批准测试、审核记录/交接事务回滚测试、401/403/409 验收、审计脱敏检查。

## 六、PR 正文模板

```markdown
## 计划 ID
YC R2

## 完成内容
- ItemWorkflowService 提审/撤回/改稿/发布/下架/删除校验
- ReviewService 批准驳回、审核记录同事务、自审与版本冲突
- 待办/历史/记录接口与审核工作台页面

## 依赖与接口变化
- 依赖 YYX R1 的 ArticleMapper 锁定查询与条件更新（当前为最小契约）
- 依赖 SR R1 的 CategoryAccessService
- 无新增表/字段，无 004 迁移

## 测试
- 全量 75 项通过；本模块 45 项
- 并发批准 1 成功 1 冲突；审核记录失败状态回滚

## 未完成/风险
- article Controller 的提审/发布入口由袁运轩接入
- 真实 MySQL 与外部 Tomcat 由高鸣远复验
```
