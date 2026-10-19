# 第一轮公共接入契约

计划 GMY R1　版本 GMY-R1-1　2026年10月19日

本文件和 [机器可读清单](contracts/r1-contracts.json) 固定第一轮协作使用的字段、错误码、权限码、接口及版本约定。Java 公共类型已经交付；认证、分类、信息和审核认领的业务实现仍由其维护人完成。清单里的业务接口不是已经开放的 API，也没有假登录或默认放行 Bean。

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
| RegisterRequest | username 4 到 50 位字母数字下划线；password 8 到 64 字符；displayName 1 到 80 字符 | 郭金滔 |
| CurrentUser | userId、username、displayName、roleCodes、permissionCodes；不含口令或哈希 | 郭金滔 |
| CategoryCreateRequest | name 1 到 80；parentId 为 null 根或正整数；sortOrder 非负，缺省 0 | 孙睿 |
| CategoryOption | id、name、children，公开树不包含管理和停用字段 | 孙睿 |
| ItemDraftRequest | title 1 到 200、content 1 到 20000 纯文本、categoryId、itemType、eventTime、location 和 contactInfo 各不超过 100 | 袁运轩 |
| ItemUpdateRequest | 草稿字段加 version，操作者来自真实 Session | 袁运轩 |
| ReviewDecisionRequest | version、APPROVED 或 REJECTED、最多 500 字符 comment，驳回需原因 | 杨程 |
| ClaimCreateRequest | claimDescription 1 到 1000、contactInfo 1 到 100；均为私有数据 | 杨程 |
| ClaimReviewRequest | 认领 version、批准或驳回、最多 500 字符 comment | 杨程 |
| HandoverRequest | 物品 version；FOUND 另需 claimId 和 claimVersion，分清两个版本 | 杨程 |

创建请求不采信作者、申请人、审核人、角色、状态、版本或审计时间等服务器字段。公开摘要只返回 id、title、itemType、categoryId、categoryName、eventTime、location、publishedAt、resolutionStatus；公开详情额外返回 content。私有详情按权限增加 contactInfo、authorId、status、version 等，不直接返回 Entity。

公开列表和详情不能包含密码、密码哈希、联系方式、认领证据、完整学号或权限集合。LOST 联系方式走登录后的单条入口并审计；FOUND 另由认领策略判断。拥有查看内容的权限，不自动获得查看联系方式的权限。

## 登录与同源约定

郭金滔实现真实 Session 和 CSRF。统一采用 JSESSIONID 会话 Cookie、`X-CSRF-Token` 请求头；规划的 GET `/api/auth/csrf` 返回 Result 的 data.token，并与服务端匿名或登录会话关联。客户端先获取令牌并保留 Cookie，再执行注册、登录及其他写操作；登录成功后重新取得令牌，退出后原会话与令牌失效。令牌和口令不记录日志，Cookie 不允许跨站通配凭据。

静态 GET／HEAD 的 pages 和 assets 可以公开读取，其页面不得嵌入私有业务数据；实际数据 API 仍由权限组件保护。身份停用与撤权必须影响旧会话，不能永远相信登录时缓存的角色权限。CSRF 和 Session 代码属于 GJT R1，本轮没有临时开放的替代实现。

## 权限码与角色

41 个稳定权限名由 `common.security.PermissionCodes` 发布，全部码及维护人见 JSON 的 permissions；保留孙睿分支已使用的 category:add、category:view，不要求分类模块另造命名。

角色沿用 `RoleCode` 的 ADMIN、EDITOR、REVIEWER、VIEWER，EDITOR 显示为学生。普通注册只分配 EDITOR；学生仅操作自己的资源，只读角色无写权限。具体角色授权、种子及 Session/AOP 由郭金滔实现，这些常量本身不会授权任何请求。

批量发布同时要求 item:batch 与 item:publish；批量下架同时要求 item:batch 与 item:offline:any；批量删除同时要求 item:batch 与 item:delete:any。权限集合默认按 ALL 检查；需要 ANY 时必须在注解中明确写出，并继续检查每条所有权和状态。

## 模块接口与唯一维护人

| 契约 | 约定 | 实现与调用 |
|---|---|---|
| auth.service.CurrentUserProvider | requireCurrentUser 返回 CurrentUser；hasPermission 判断权限；匿名的 require 返回 401 | 郭金滔实现，全组使用真实用户，不信任客户端 actorId |
| auth.annotation.RequiresPermission | value 为 String 数组；logical 默认 ALL，显式支持 ANY；缺权限 403 | 郭金滔实现，父子容器 AOP 已启用，不表示业务已鉴权 |
| category.service.CategoryAccessService | isBindable(Long)、requireBindable(Long)、publicTree() | 沿用孙睿已提交分支的契约；null 为 400，不存在 404，自身或祖先停用 409 |
| review.service.ItemWorkflowService | SUBMIT、WITHDRAW、APPROVE、REJECT、PUBLISH、OFFLINE、EDIT_DRAFT、DELETE、RESOLVE | 杨程实现，输入 itemId、version 和命令数据，返回 id、状态、处理状态及新 version |
| OperationAuditService | 接收操作者、模块、动作、目标、requestId、结果和耗时 | 杨程实现；不得接收完整请求体；审核记录和业务状态同事务 |

接口类型留在各自模块，第一轮不注册空实现。依赖方向为 ArticleService → ItemWorkflowService → ArticleMapper，工作流不能反向依赖 ArticleService。修改正文且伴随状态变化的复合操作在同一 REQUIRED 事务内完成；独立 EDIT_DRAFT 只做显式状态转换，客户端后续编辑使用返回的新版本。

新模块沿既有包目录放置 Controller、DTO、Service、Entity、Mapper；Mapper 标记 @Mapper，XML 放 `src/main/resources/mapper/模块名`。页面放 `src/main/webapp/pages/模块名`，公共 CSS 与 JS 放 assets。Spring 已统一映射 pages 和 assets，新增页面无需修改公共 MVC 配置。

## 第一轮边界与后续合并

孙睿远程分支已存在分类 API，但尚未合入 develop；其管理接口还等待郭金滔的真实权限组件，不能以 TODO 或放行方式判定完成。高鸣远本轮只对齐契约，不替组员合并或代写这部分业务。

SQL 十表的 86 个字段、约束和索引见 [结构清单](database-schema.md)。001、002 保持已执行版本；003 为郭金滔权限种子提案预留，尚未创建或应用。新增字段、权限码和共享接口变更通过独立说明交高鸣远审核，并更新版本与测试。
