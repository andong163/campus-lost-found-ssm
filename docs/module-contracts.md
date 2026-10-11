# 模块接入约定

## 公共请求与响应

基础地址 `/cms/api`；Controller 接收 DTO，Service 校验业务与管理事务，Mapper 访问数据库。禁止将 Entity 直接作为注册、角色分配和文章状态修改的请求体，防止客户端覆盖内部字段。

成功使用 `Result.ok(data)`，HTTP 200。错误使用 `BusinessException(HttpStatus.CONFLICT, "可展示的提示")` 等由全局处理。约定 400 参数、401 未登录、403 无权限、404 不存在、409 冲突、500 系统异常；客户端不接收堆栈、SQL或口令。新增接口如需 201，需同时扩展 Result 的成功工厂并保持 HTTP/code 一致。

分页 DTO 可继承 `PageQuery`，Controller 使用 `@Valid @ModelAttribute`；默认 page=1、size=10，上限100。Service 可把 MP IPage 转为 `PageResult`。排序只能映射允许字段，不能把请求内容直接拼接进 SQL。模糊 `%keyword%` 不会自动受益于普通索引，性能优化需 EXPLAIN 和 SQL 次数证据；标题 FULLTEXT 仅在明确使用 MATCH 时生效，不替代中文包含查询语义。

## 数据库与基础实体

可变业务实体继承 `BaseEntity`，共用 id/createdAt/updatedAt/deleted/version。MP 的 insert/updateById 路径自动填充并支持逻辑删除、乐观锁；自定义 SQL、UpdateWrapper 或批量 SQL 必须自行校验更新条件、时间和版本，不能假定插件会全部代办。

用户与角色代码软删除后仍保留唯一值；第一版不复用用户名。根分类 parent_id=NULL，多级循环、同级重名在孙睿 Service 检查。删除或停用父分类的子树规则必须在公开查询中同样应用。审核记录和日志是追加型记录，不继承软删除业务实体；用户软删除保留历史引用。

## 模块任务与接口前缀

| 成员 | 包与接口前缀 | 接入重点 |
|---|---|---|
| 郭金滔 | auth；`/auth`、`/users`、`/roles`、`/permissions` | 当前用户入口、登录拦截与 AOP 权限；普通注册不能选择管理角色 |
| 孙睿 | category；`/categories` | 分类树、状态、循环及文章关联校验 |
| 袁运轩 | article；`/articles`、`/public/articles` | 内容维护、DTO、所有权、公开状态过滤、批量与查询优化 |
| 杨程 | review/audit；`/reviews`、`/operation-logs` | 审核事务、共用状态入口、日志字段、联调集 |

这些接口是规划，当前仅实现 `/api/system/health` 与 `/api/system/readiness`。登录拦截和权限切面尚未实现，因此业务接口接入后必须同时补充鉴权；不能把健康检查当作已实现登录验证。

## 文章与审核共同规则

持久化状态使用 `ArticleStatus` 的名称：DRAFT、PENDING_REVIEW、APPROVED、PUBLISHED、REJECTED、OFFLINE。

主流程 DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED → OFFLINE；REJECTED 修改后重提。待审核禁止改正文，已发布修改前退回草稿停止公开，再审核。第一版分类一对多文章。

由杨程维护共同状态服务，袁运轩调用提交、发布和下架入口。更新包含预期状态和版本；影响行数0返回409。审核状态与记录同一事务。批量第一版整批成功或回滚，事务由公开 Service 方法承担，不能内部自调用绕过代理；空批次与重复ID先校验。

请求诊断 `RequestLogFilter` 只记录方法、路径、状态、耗时与 requestId，不保存请求体。杨程实现的数据库审计记录是另一组件，须排除敏感字段；普通日志失败不应回滚正常业务，审批记录则属于审批事务的一部分。
