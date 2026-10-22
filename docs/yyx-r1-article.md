# YYX R1 物品数据访问与本人草稿接入记录

记录时间：2026-10-21。负责人：袁运轩。已将本分支同步到包含郭金滔身份和孙睿分类模块的最新 `origin/develop`，接入真实身份与统一分类校验。本轮为物品草稿写接口补上会话绑定的 CSRF 令牌校验。

## 本轮完成

- `Article` 继承公共 `BaseEntity`，与 `cms_article` 的作者、分类、双状态、事件时间、联系方式和审计字段对应。LOST 与 FOUND 共用一张表。MP `insert/selectById` 负责草稿保存与普通详情读取。
- `ArticleMapper.selectDraftByIdAndAuthor(id, authorId)` 同时检查 `DRAFT`、作者和 `deleted=0`。服务接入时仍须由郭金滔的组件先完成登录、权限和真实身份判定，不应把请求中的 ID 当作作者。
- `ArticleMapper.selectByIdForUpdate(id)` 供杨程在现有事务内先锁物品行；`updateStatusIfVersion(id, expectedStatus, nextStatus, expectedVersion)` 检查未删除、旧状态和版本，更新状态、时间和版本，返回 0 时由工作流转换为 409。SQL 仅提供原子写入原语，不决定允许的状态转换。
- `CreateDraftRequest` 只允许编辑 title、content、itemType、categoryId、eventTime、location、contactInfo；JSR303 检查必填、长度、正分类 ID、LOST/FOUND 枚举和不晚于当前时间。`toNewArticle(authenticatedAuthorId)` 固定 DRAFT、OPEN，审计字段和初始版本由公共 MP 填充。此方法只能在真实授权与分类可绑定校验之后调用。
- `PrivateArticleDetail` 包含联系方式，限定授权后的本人读取；`PublicArticleView` 不含联系方式、作者 ID、版本或认领证据，并拒绝从未发布或已删除实体生成。公开分类及全部祖先有效性仍须由孙睿组件检查。

## HTTP 接口接入状态

| 接口 | 处理顺序 | 未接入依赖 |
|---|---|---|
| `POST /api/items`，新增本人草稿 | 已实现。真实 Session 身份与 `article:create` 权限 → `X-CSRF-Token` 会话令牌校验 → `@Valid CreateDraftRequest` → `CategoryAccessService.requireBindable(Long)` → 事务中按真实作者 ID 创建 DRAFT/OPEN → 返回私有 DTO | 本轮令牌校验只接入物品草稿写接口，其他模块的写接口仍由其负责人统一接入 |
| `GET /api/items/{id}`，本人草稿详情 | 已实现。真实 Session 身份、`article:edit` 权限 → `selectDraftByIdAndAuthor(id, currentUser.id)` → 本人私有 DTO；不存在或其他作者一律 404 | 无分类依赖 |

Controller 路径从 `/api/items` 开始，部署上下文 `/cms` 由容器添加，实际地址为 `/cms/api/items` 与 `/cms/api/items/{id}`。已新增正式 `ArticleDraftController` 和 `ArticleDraftService`，没有模拟身份或分类的运行时组件。草稿创建请求 DTO 只声明可编辑字段；传入 authorId、status 或 version 的请求在当前 MVC 配置下返回 400，服务端也固定作者、DRAFT/OPEN 和初始版本。分类不存在、删除或自身/任一祖先停用时由孙睿统一服务拒绝绑定。

登录时身份模块在新会话内换发随机令牌。登录后的同源页面调用 `GET /api/auth/csrf` 获取令牌，并在 `POST /api/items` 的 `X-CSRF-Token` 请求头中提交；缺失、错误或其他会话的令牌返回 403，不写入草稿。令牌接口设置 `Cache-Control: no-store`。该校验目前仅覆盖 YYX R1 的写接口，不代表其他成员的写接口已有 CSRF 防护。

## 跨模块待确认

| 责任人 | 待确认的具体接口或约定 |
|---|---|
| 郭金滔 | 本人详情和创建已使用 `CurrentUserContext.require()`、`@RequirePermission("article:edit"/"article:create")`；请审核本轮对登录会话增加的令牌签发与 `GET /api/auth/csrf`，并决定其他模块写接口何时统一接入。 |
| 孙睿 | 已接入合并后的 `CategoryAccessService.requireBindable(Long)`；分类不存在返回 404、停用返回 409。若后续增加分类停用/删除写操作，需要共同确认与内容绑定的事务并发策略。 |
| 高鸣远 | `POST /api/items`、`GET /api/items/{id}` 路径和响应字段；公共错误码/权限码；MySQL 8 真库字段核对、索引与未来迁移编号；主 OpenAPI/API 文档统一更新。现有 `cms_article.title` 为 VARCHAR(200)，需求上限是 100，本轮在 DTO 层限制，是否收紧列由高鸣远审核。 |
| 杨程 | 已实现的 `selectByIdForUpdate` 和 `updateStatusIfVersion` 参数、返回 0 转 409 的策略；事务中始终先锁物品再处理申请；是否需要对审核状态写入附加作者/处理状态条件；`ItemWorkflowService` 由杨程维护，本文未实现状态转换规则。 |

## 测试与未验证

原有独立 H2 `article_r1` 测试验证实体保存、映射、本人草稿过滤、行锁查询、条件更新、DTO 校验和公开序列化。新增独立 H2 `article_http_r1` 测试以真实注册、登录、Session、权限切面及孙睿分类服务验证创建与本人详情：正常创建、固定作者/状态/版本、伪造字段 400、分类不存在 404/停用 409、未登录 401、无权限 403、本人详情 200、他人详情 404；缺失、错误和其他会话的 CSRF 令牌均 403。测试不修改业务数据库。尚未验证分类并发绑定及外部 MySQL 8 锁行为；其他模块写请求的令牌覆盖仍需各负责人协同。

## AI 辅助与人工核查记录

此前 AI 辅助依据袁运轩提出的 YYX R1 范围、两份 2026 年 10 月课程文档、仓库 `sql/init.sql` 和公共组件生成实体、Mapper XML、DTO 及隔离测试。本次 AI 辅助获取远程记录，临时保存未提交修改，把已有提交变基到最新 `origin/develop` 并恢复修改，随后新增本人草稿详情及创建 Controller、Service、会话绑定 CSRF 令牌和真实 Session/分类接口测试，并更新本文。人工检查时重点读 `ArticleMapper.xml` 的作者/状态/删除过滤和版本条件，确认 `PublicArticleView` 未泄露联系方式；核对 Controller 权限、令牌校验、服务中作者仅来自 Session、分类检查先于写入；在 MySQL 8 上重验 `FOR UPDATE` 和事务锁顺序。
