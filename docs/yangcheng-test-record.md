# 杨程模块测试记录（review / claim / audit）

日期：2026-11-17（R4 收尾）　分支：`feature/yangcheng-review-claim`　执行人：杨程（AI 辅助，需本人复跑确认）

## 一、环境与执行方式

| 项 | 实际值 |
|---|---|
| 操作系统 | Windows |
| Maven | 3.9.11（仓库内 `.mvn/settings.xml` + `.cache/m2`） |
| 编译目标 | `maven.compiler.release=11` |
| 运行 JDK | Microsoft OpenJDK 17.0.17（本机未安装 JDK 11） |
| 数据库夹具 | H2 2.2.224，`MODE=MySQL` |
| 容器 | Tomcat 9.0.122（`WarDeploymentIT` 使用测试依赖部署实际 WAR） |

本机没有 JDK 11，因此没有直接执行 `scripts/build.ps1`（脚本会校验 `JAVA_VERSION="11..."`）。使用等价的 Maven 命令，以 `--release 11` 编译，并为基线容器测试追加 `--add-opens=java.base/java.lang=ALL-UNNAMED`，否则 JDK 17 下 CGLIB 会因模块访问限制失败。交付前仍需高鸣远在 JDK 11 执行 `./scripts/build.ps1` 复跑一次。

```powershell
$env:JAVA_HOME='C:\Users\cheng\.jdks\ms-17.0.17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8'
mvn -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -B -ntp `
  -DargLine="--add-opens=java.base/java.lang=ALL-UNNAMED" clean verify
```

结果（R4 收尾）：`BUILD SUCCESS`，Surefire **169 项**、Failsafe `WarDeploymentIT` **1 项**，失败 0、错误 0、跳过 0。

## 二、分项结果

### 2.1 首轮基线（30 项，历史记录）

| 测试类 | 数量 | 结果 |
|---|---:|---|
| AdminInitializerTest | 1 | 通过 |
| AuthFlowTest | 8 | 通过 |
| ContainerSmokeTest | 1 | 通过 |
| FrameworkIntegrationTest | 6 | 通过 |
| LocalDatabaseSupportTest | 3 | 通过 |
| RequestLogFilterTest | 1 | 通过 |
| WebContractTest | 10 | 通过 |

### 2.2 杨程模块（9 类，54 项）

| 测试类 | 数量 | 覆盖点 | 结果 |
|---|---:|---|---|
| AuditTemplateTest | 4 | 成功/失败审计、requestId、匿名操作者、审计写入失败不改变业务结果 | 通过 |
| AuditServiceTest | 3 | 真实 H2 写入、操作者/动作/结果/时间过滤、倒序、字段截断、非法时间范围 | 通过 |
| ItemWorkflowTest | 14 | 提审/撤回、停用分类、驳回意见、自审、旧版本、批准发布、下架关闭申请、已通过认领阻断、LOST 找回、FOUND 交接、重复结案、删除校验、分类祖先判断 | 通过 |
| ClaimFlowTest | 10 | 申请成功/本人物品/LOST/未发布/已结案、停用分类、重复申请、撤销后重申、批准不结案、第二个批准、自审与利益回避、撤销释放名额、联系方式策略、列表权限 | 通过 |
| ConcurrentClaimApprovalTest | 1 | 两线程并发批准同一物品，恰好一个成功、一个 409，数据库仅一条 APPROVED | 通过 |
| ReviewRollbackTest | 1 | 审核记录插入失败时内容状态与版本回滚 | 通过 |
| HandoverRollbackTest | 1 | 物品结案失败时申请 COMPLETED 与时间回滚 | 通过 |
| ReviewWorkflowTest | 9 | 审核待办/历史/记录、批准驳回、自审回避、权限与版本 | 通过 |
| ReviewClaimApiTest | 11 | 401/403、CSRF、审核/认领/交接 HTTP 主流程、审计 requestId、自审失败审计、私密字段不泄露、交接上下文、审核记录权限、空筛选值、静态页面、参数校验 | 通过 |

合计：**54 项通过**。

### 2.3 R4 全量回归（当前 develop 基线）

| 范围 | 数量 | 结果 |
|---|---:|---|
| Surefire 全部单测 | 169 | 通过 |
| Failsafe `WarDeploymentIT`（真实 WAR 部署 Tomcat 9） | 1 | 通过 |
| R4 重点回归（ItemWorkflow/ClaimFlow/Concurrent/Handover/ReviewClaimApi） | 37 | 通过 |

R4 关键修复：Article 状态枚举断言、HTTP 测试登录会话从数据库重新装载后的 `claim:create`/`claim:cancel:own`/`claim:handover:own` 权限种子、POST 请求 `X-CSRF-Token` 会话令牌与请求头。

## 三、关键证据

1. **并发批准**：`ConcurrentClaimApprovalTest` 用两个线程同时批准同一物品的两条 PENDING 申请，结果为 1 个成功、1 个 409；`countApprovedByItem=1`，数据库中 APPROVED 记录数 1。实现依靠“物品行 `FOR UPDATE` → 申请行 `FOR UPDATE` → 条件更新”，数据库 `approved_article_id` 唯一索引作为最后防线。
2. **审核事务回滚**：`ReviewRollbackTest` 动态代理 `ReviewRecordMapper.insert` 抛异常；调用后文章仍为 `PENDING_REVIEW`、version 不变、审核记录 0 条，证明状态更新和记录追加在同一事务。
3. **交接事务回滚**：`HandoverRollbackTest` 让 `ArticleMapper.resolve` 抛异常；调用后申请仍为 `APPROVED`、`completed_at` 为空、物品仍 `OPEN`、`resolved_at` 为空、version 不变。
4. **自审与利益回避**：HTTP 测试以审核员身份处理本人发布的物品返回 403，并写入 `REVIEW_DECIDE/FAILURE` 审计；Service 测试覆盖“不能批准本人申请”“不能批准本人发布物品的申请”。
5. **联系方式边界**：未通过核实的其他学生读取 `/items/1/found-contact` 返回 403；作者、已通过申请人、审核人员可读；成功和失败读取都写 `FOUND_CONTACT_READ` 审计。审核待办视图不含 `content`，交接上下文不含 `contactInfo`。
6. **审计关联**：审核决定成功后，`sys_operation_log.request_id` 与响应头 `X-Request-Id` 一致；日志字段只有操作者、模块、动作、目标、结果、耗时、requestId，没有正文/联系方式/密码列。
7. **WAR 内容**：`target/cms.war` 已由 `WarDeploymentIT` 部署验证，包含 `review/review.html`、`claim/{manage,mine,handover}.html`、`audit/logs.html`、`yangcheng/{common.css,common.js}`；不包含 `jdbc-local`、`com/team/cms/support`、`com/team/cms/tools` 或 `*Test.class`。

## 四、未执行/待复验

| 项 | 原因 | 下一步 |
|---|---|---|
| JDK 11 下的 `scripts/build.ps1` | 本机只有 JDK 17 | 高鸣远在 JDK 11 环境复跑并记录 |
| 真实 MySQL 的 Service 级并发 | 本模块并发测试在 H2 MySQL 模式完成；基线已在真实 MySQL 验证生成列唯一约束 | 用专用库复跑并发批准/交接回滚，保留 SQL 日志 |
| 外部 Tomcat 手工部署 | `WarDeploymentIT` 已用嵌入式 Tomcat 9 部署实际 WAR | 最终由高鸣远在外部 Tomcat 9 复验 |
| 页面浏览器截图 | 页面为静态 HTML，未启动浏览器做视觉验收 | 接入公共登录页后由杨程实际点击并截图 |

以上未执行项不能写成“已通过”；本记录只声明表格中列出的自动化结果。
