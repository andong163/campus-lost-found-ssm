# YYX R1 物品数据访问与本人草稿接入记录

记录时间：2026-10-19。负责人：袁运轩。本记录对应尚未合入真实身份和分类组件时可独立验证的部分，不表示第一轮接口验收完成。

## 本轮完成

- `Article` 继承公共 `BaseEntity`，与 `cms_article` 的作者、分类、双状态、事件时间、联系方式和审计字段对应。LOST 与 FOUND 共用一张表。MP `insert/selectById` 负责草稿保存与普通详情读取。
- `ArticleMapper.selectDraftByIdAndAuthor(id, authorId)` 同时检查 `DRAFT`、作者和 `deleted=0`。服务接入时仍须由郭金滔的组件先完成登录、权限和真实身份判定，不应把请求中的 ID 当作作者。
- `ArticleMapper.selectByIdForUpdate(id)` 供杨程在现有事务内先锁物品行；`updateStatusIfVersion(id, expectedStatus, nextStatus, expectedVersion)` 检查未删除、旧状态和版本，更新状态、时间和版本，返回 0 时由工作流转换为 409。SQL 仅提供原子写入原语，不决定允许的状态转换。
- `CreateDraftRequest` 只允许编辑 title、content、itemType、categoryId、eventTime、location、contactInfo；JSR303 检查必填、长度、正分类 ID、LOST/FOUND 枚举和不晚于当前时间。`toNewArticle(authenticatedAuthorId)` 固定 DRAFT、OPEN，审计字段和初始版本由公共 MP 填充。此方法只能在真实授权与分类可绑定校验之后调用。
- `PrivateArticleDetail` 包含联系方式，限定授权后的本人读取；`PublicArticleView` 不含联系方式、作者 ID、版本或认领证据，并拒绝从未发布或已删除实体生成。公开分类及全部祖先有效性仍须由孙睿组件检查。

## 后续 HTTP 接口方案，当前未开放

| 接口 | 处理顺序 | 未接入依赖 |
|---|---|---|
| `POST /api/items` | Session 身份与写权限、CSRF → `@Valid CreateDraftRequest` → 统一分类可绑定检查 → 在事务中按真实作者 ID 创建 DRAFT/OPEN → 返回本人私有 DTO | 郭金滔 CurrentUser、权限/CSRF；孙睿 CategoryAccessService |
| `GET /api/items/{id}`，本人草稿详情 | Session 身份与读取权限 → `selectDraftByIdAndAuthor(id, currentUser.id)` → 404/403 策略按公共约定 → 返回私有 DTO | 郭金滔 CurrentUser、权限码及隐藏资源策略 |

这两个路径仅是待确认接口方案。仓库没有正式 article Controller 或可绕过鉴权的运行时替身。草稿创建不接受 authorId、status、resolutionStatus 或 version 请求字段。分类不存在、删除或任何祖先停用时须拒绝绑定，不能仅检查 categoryId 为正数。

## 跨模块待确认

| 责任人 | 待确认的具体接口或约定 |
|---|---|
| 郭金滔 | CurrentUser 的取值方式和 ID 类型；创建、本人详情权限码与注解；Session 失效 401、越权 403/隐藏资源 404；写接口 CSRF 接入方式。 |
| 孙睿 | `CategoryAccessService` 可绑定校验签名、祖先有效性、分类行锁与内容绑定的锁顺序；失败码；公开投影前的统一分类可见性判断。 |
| 高鸣远 | `POST /api/items`、`GET /api/items/{id}` 路径和响应字段；公共错误码/权限码；MySQL 8 真库字段核对、索引与未来迁移编号；主 OpenAPI/API 文档统一更新。现有 `cms_article.title` 为 VARCHAR(200)，需求上限是 100，本轮在 DTO 层限制，是否收紧列由高鸣远审核。 |
| 杨程 | 已实现的 `selectByIdForUpdate` 和 `updateStatusIfVersion` 参数、返回 0 转 409 的策略；事务中始终先锁物品再处理申请；是否需要对审核状态写入附加作者/处理状态条件；`ItemWorkflowService` 由杨程维护，本文未实现状态转换规则。 |

## 测试与未验证

本轮使用独立 H2 `article_r1` 和 `article-schema-test.sql`，验证实体保存、映射、本人草稿过滤、行锁查询、条件更新、DTO 校验和公开序列化。测试中的作者 101 与分类 202 只是隔离库数据，不是正式运行时身份或分类组件。尚未验证真实 Session/权限/CSRF、分类祖先及并发绑定、外部 MySQL 8 锁行为和两个 HTTP 接口。第一轮完整验收还需上述真实组件合入后的 401/403/越权、分类失效、数据库和 HTTP 测试。

## AI 辅助与人工核查记录

本次 AI 辅助依据袁运轩提出的 YYX R1 范围、两份 2026 年 10 月课程文档、仓库 `sql/init.sql` 和现有公共组件，生成实体、Mapper XML、DTO、隔离测试及本文。人工检查时重点读 `ArticleMapper.xml` 的作者/状态/删除过滤和版本条件，确认 `PublicArticleView` 未泄露联系方式；核对未来 Controller 的身份只来自 Session，分类检查先于写入；在 MySQL 8 上重验 `FOR UPDATE` 和事务锁顺序。本记录只记本次实际辅助与验证，不代替袁运轩的代码理解和人工验收。
