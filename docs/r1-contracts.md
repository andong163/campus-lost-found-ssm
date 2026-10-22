# 第一轮公共接入契约

计划 GMY R1　总合并契约 R1-INTEGRATED-2　2026年10月22日

本文件和 [机器可读清单](contracts/r1-contracts.json) 固定第一轮总合并后的接入方式。保留组员已有方法、权限码和请求字段；尚未实现的审核认领命令仍是后续轮次契约，不创建空服务或假业务 API。各轮实际 PR 与验收见 team-iteration-plan.md。

## 公共类型与请求规则

- 成功响应使用 Result 的 code、message、data；HTTP 状态与 code 一致。200 成功，400 校验或格式错误，401 未登录，403 无权限，404 不存在或不能公开，405 方法不支持，409 状态、版本或数据约束冲突，415 媒体类型不支持，500 未预期错误，503 数据库等基础服务不可用。
- 数据约束和并发异常由公共 Advice 转为安全 409；数据库资源及事务连接建立失败转为 503。响应不包含 SQL、堆栈或口令；业务仍须在 Service 主动检查条件并解释合法的业务失败。
- 单条修改、状态转换、逻辑删除继承 `VersionRequest`：请求提供非空、非负 int32 字段 version，与同一事务内的条件更新一起比较。创建版本由服务器设为 0。更新影响行数为 0 返回 409，不能只检查请求版本后再无条件写库。
- `BatchIdsRequest` 的 ids 必须为 1 到 100 个互不重复的正 int64；小数拒绝。批量协议仍为 ids，服务在同一事务内检查每条权限、状态与数据库版本，按统一顺序锁行或条件更新；任一失败整批回滚。
- 枚举只能用已约定的字符串，例如 LOST、FOUND，数字序号拒绝；JSON 整数不接受小数截断。时间使用 Asia/Shanghai 约定的本地 ISO 日期时间，例如 2026-10-19T12:00:00，对应 MySQL DATETIME，不以时间戳数字替代。
- 分页沿用 PageQuery，page 默认 1、size 默认 10、上限 100；排序字段映射白名单并追加 id 稳定排序；LIKE 参数绑定，不拼接用户 SQL。

## DTO 字段与私密边界

完整字段及长度见 JSON 的 dtoContracts。主要约定如下。

| 请求或响应 | 字段与限制 | 维护人 |
|---|---|---|
| RegisterRequest | username 4 到 30 位字母数字下划线；password 8 到 64 字符；displayName 2 到 20 字符 | 郭金滔 |
| CurrentUser | id、username、displayName、roles、permissions；不含口令或哈希 | 郭金滔 |
| CategoryCreateRequest | name 1 到 80；parentId 为 null 根或正整数；sortOrder 非负，缺省 0 | 孙睿 |
| CategoryOption | id、name、children，公开树不包含管理和停用字段 | 孙睿 |
| CreateDraftRequest（原规划 ItemDraftRequest） | title 2 到 100、content 10 到 5000 纯文本、categoryId 正整数、itemType、eventTime 不晚于当前，location 和 contactInfo 各 2 到 100 | 袁运轩 |
| ItemUpdateRequest | 草稿字段加 version，操作者来自真实 Session | 袁运轩 |
| ReviewDecisionRequest | version、APPROVED 或 REJECTED、最多 500 字符 comment，驳回需原因 | 杨程 |
| ClaimCreateRequest | claimDescription 1 到 1000、contactInfo 1 到 100；均为私有数据 | 杨程 |
| ClaimReviewRequest | 认领 version、批准或驳回、最多 500 字符 comment | 杨程 |
| HandoverRequest | 物品 version；FOUND 另需 claimId 和 claimVersion，分清两个版本 | 杨程 |

创建请求不采信作者、申请人、审核人、角色、状态、版本或审计时间等服务器字段。公开摘要只返回 id、title、itemType、categoryId、categoryName、eventTime、location、publishedAt、resolutionStatus；公开详情额外返回 content。私有详情按权限增加 contactInfo、authorId、status、version 等，不直接返回 Entity。

公开列表和详情不能包含密码、密码哈希、联系方式、认领证据、完整学号或权限集合。LOST 联系方式走登录后的单条入口并审计；FOUND 另由认领策略判断。拥有查看内容的权限，不自动获得查看联系方式的权限。

## 登录与同源约定

统一采用 JSESSIONID 会话 Cookie、`X-CSRF-Token` 请求头。YYX R1 对接郭金滔身份模块，在每次登录的新会话内换发令牌；登录后 GET `/api/auth/csrf` 返回 Result 的 **data 字符串**，不是 data.token。新增草稿时携带该字符串，缺失、错误或其他会话令牌返回 403。令牌接口 no-store；重新登录和退出使旧会话令牌失效。令牌和口令不记录日志，Cookie 不允许跨站通配凭据。

R1 的令牌检查只覆盖 POST `/api/items`；其他写接口的统一覆盖、身份停用和撤权影响旧会话仍按郭金滔后续任务验收，不宣称当前已完成。静态 pages 和 assets 可以公开读取，但不嵌入私有业务数据；实际数据 API 仍由权限组件保护。

## 权限码与角色

41 个规划权限常量由 `common.security.PermissionCodes` 发布，不自动授权。本轮实际接口保留现有的 article:create、article:edit、category:add、category:view、audit:view；草稿创建使用 article:create，本人草稿详情使用 article:edit，并继续限制本人资源。没有直接把规划 item:* 替换进旧种子或注解。后续若统一权限命名，由郭金滔提供新编号迁移和兼容方案，不回写已发布的 003。

角色沿用 `RoleCode` 的 ADMIN、EDITOR、REVIEWER、VIEWER，EDITOR 显示为学生。普通注册只分配 EDITOR；学生仅操作自己的资源，只读角色无写权限。具体角色授权、种子及 Session/AOP 由郭金滔实现，这些常量本身不会授权任何请求。

后续批量发布同时检查 item:batch 与 item:publish；批量下架检查 item:batch 与 item:offline:any；批量删除检查 item:batch 与 item:delete:any。这些是 R2～R4 的接入要求，当前单字符串 RequirePermission 不具备数组或 ANY 功能，不能只改注解参数就认为已实现多权限校验。

## 模块接口与唯一维护人

| 契约 | 约定 | 实现与调用 |
|---|---|---|
| auth.security.CurrentUserContext | get() 允许为空；require() 返回 CurrentUser，匿名返回 401；用户对象 hasPermission(code) | 沿用郭金滔实际实现；不信任客户端 actorId |
| auth.security.RequirePermission | value 为单个 String；未登录 401、缺权限 403；现有 ADMIN 角色放行 | 沿用实际注解，不另造 RequiresPermission；扩展多权限由郭金滔后续统一 |
| category.service.CategoryAccessService | isBindable(Long)、requireBindable(Long)、publicTree() | 沿用孙睿已提交分支的契约；null 为 400，不存在 404，自身或祖先停用 409 |
| review.service.ItemWorkflowService | SUBMIT、WITHDRAW、APPROVE、REJECT、PUBLISH、OFFLINE、EDIT_DRAFT、DELETE、RESOLVE | 杨程实现，输入 itemId、version 和命令数据，返回 id、状态、处理状态及新 version |
| audit.service.AuditService / AuditTemplate | 接收操作者、模块、动作、目标、requestId、结果和耗时 | 杨程实际名称；操作日志独立事务隔离失败；审核记录仍与业务状态同事务 |

接口类型留在各自模块，第一轮不注册空实现。依赖方向为 ArticleService → ItemWorkflowService → ArticleMapper，工作流不能反向依赖 ArticleService。修改正文且伴随状态变化的复合操作在同一 REQUIRED 事务内完成；独立 EDIT_DRAFT 只做显式状态转换，客户端后续编辑使用返回的新版本。

总合并后的 Article 沿用公共枚举并提供 isFound()/isLost()；Mapper 同时保留袁运轩的本人草稿查询、updateStatusIfVersion 和杨程的 updateStatus、resolve。统一 selectByIdForUpdate(Long) 接受原 long 调用的自动装箱，必须在事务内调用。完整参数见 JSON 的 interfaces.ArticleMapper；方法返回零行不能退回无条件更新，具体审核与结案服务仍在后续轮次实现。

新模块沿既有包目录放置 Controller、DTO、Service、Entity、Mapper；Mapper 标记 @Mapper，XML 放 `src/main/resources/mapper/模块名`。页面放 `src/main/webapp/pages/模块名`，公共 CSS 与 JS 放 assets。Spring 已统一映射 pages 和 assets，新增页面无需修改公共 MVC 配置。

## 第一轮边界与后续合并

认证和分类已进入 develop，分类管理接口使用真实权限组件。总合并接收各人第一轮原始提交，并追加必要的兼容修复；不重写个人分支，也不实现后续轮次的工作流、认领、页面或性能优化。

SQL 十表的 86 个字段、约束和索引见 [结构清单](database-schema.md)。001、002、003 均保留现有脚本；003 已由郭金滔提交，总合并不自动执行它或写入业务库。新增字段、权限码和共享接口变更交高鸣远审核，后续迁移从 004 起登记，并更新版本与测试。
