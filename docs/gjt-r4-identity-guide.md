# 身份模块操作说明（GJT R4）

本文件是郭金滔第四轮交付的身份模块操作说明书，覆盖运行、初始化、权限模型、会话与 CSRF 约定、
权限矩阵和排障步骤。配套材料：`docs/gjt-r4-test-record.md`（测试记录与问题修复过程）、
`docs/gjt-r4-apifox-cases.json`（可导入 ApiFox 的接口用例）。
页面使用说明见 `docs/gjt-r3-pages.md`。

## 1 模块边界

| 内容 | 位置 |
| --- | --- |
| 实体与 Mapper | `com.team.cms.auth.entity`、`com.team.cms.auth.mapper` |
| 注册/登录/退出/身份 | `com.team.cms.auth.controller.AuthController` |
| 用户、角色、权限管理 | `UserController`、`RoleController`、`PermissionController` |
| 登录拦截器 | `com.team.cms.auth.web.AuthInterceptor`（在 `SpringMvcConfig` 注册） |
| 权限注解与切面 | `com.team.cms.auth.security.RequirePermission`、`PermissionAspect` |
| 会话身份载体 | `CurrentUser`、`CurrentUserContext` |
| 口令哈希与 CSRF | `PasswordHasher`、`CsrfTokenService` |
| 表结构 | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` |

其它模块只允许通过 `CurrentUserContext.require()` 取当前用户，用 `@RequirePermission("权限码")`
声明接口权限，不得自行读取请求参数中的用户主键。

## 2 本地运行

### 2.1 准备数据库

```powershell
$env:CMS_DB_URL='jdbc:mysql://127.0.0.1:3306/cms_content?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:CMS_DB_USERNAME='root'
$env:CMS_DB_PASSWORD='你的本地密码'
```

执行 `sql/init.sql` 建表。`003-auth-permission-seed.sql` 是身份权限种子，高鸣远的总合并不自动执行它，
需要手动执行一次（或在专用测试库中执行）。执行后角色与权限的对应关系见第 4 节。

### 2.2 一次性管理员初始化

系统没有内置默认口令，也不把明文写进仓库。首次启动前设置环境变量：

```powershell
$env:CMS_ADMIN_USERNAME='admin'      # 可选，默认 admin
$env:CMS_ADMIN_PASSWORD='至少8位、不要提交到仓库'
```

`AdminInitializer` 在 Spring 容器刷新完成后执行一次：账号已存在则跳过，不存在则创建并赋予 `ADMIN`。
未配置 `CMS_ADMIN_PASSWORD` 时只打印一条跳过日志，不会创建账号，也不会让启动失败。
初始化完成后应当清掉该环境变量，管理员密码由页面登录后自行修改（后续轮次提供改密入口）。

### 2.3 启动

标准方式（需要 JDK 11）：

```powershell
./scripts/run-dev.ps1 -Port 8088
```

若本机只有 JDK 17/21，可直接用 Maven 打包并内嵌 Tomcat 启动：

```powershell
$env:JAVA_HOME='你的JDK路径'
./scripts/build.ps1          # 或在 IDEA 中 Maven package
```

上下文路径固定为 `/cms`，接口前缀 `/cms/api`，页面在 `/cms/pages/auth/`。
健康检查：`GET /cms/api/system/health`，就绪检查：`GET /cms/api/system/readiness`。

## 3 身份与权限模型

### 3.1 口令

- 算法 PBKDF2WithHmacSHA256，迭代 100000 次，16 字节随机盐，256 位输出。
- 存储格式 `迭代次数:盐Base64:哈希Base64`，`sys_user.password_hash` 长度足够容纳。
- 校验使用常量时间比较；任何接口都不返回口令或哈希。
- 注册请求只接受 `username`、`displayName`、`password`，多传的 `role`、`permissions` 等字段被忽略，
  注册一律只赋予 `EDITOR`。

### 3.2 角色

| 代码 | 名称 | 说明 |
| --- | --- | --- |
| `ADMIN` | 管理员 | 具备全部已发布权限码，并在切面内直接放行 |
| `EDITOR` | 学生 | 只能使用本人范围的业务接口 |
| `REVIEWER` | 审核人员 | 审核与认领处理 |
| `VIEWER` | 只读用户 | 当前不授予任何权限码 |

`ADMIN` 的放行规则写在 `PermissionAspect`：`hasRole("ADMIN") || hasPermission(注解值)`。
这样即使新权限码还没进种子，管理员也不会被挡在系统外，保证「管理员可管理性」。

### 3.3 权限码

权限码随迁移分批发布，编号 003 起由高鸣远统一登记，发布后不回写旧脚本：

| 迁移 | 新增权限码 | 默认授权 |
| --- | --- | --- |
| `003-auth-permission-seed.sql` | 身份与管理类 10 个：`user:manage`、`role:manage`、`permission:manage`、`article:create`、`article:edit`、`article:publish`、`review:handle`、`claim:handle`、`claim:resolve`、`audit:view` | ADMIN 全部；EDITOR `article:*`；REVIEWER `article:publish`、`review:handle`、`claim:handle` |
| `004-item-review-permissions.sql` | 信息与审核类 9 个：`item:create`、`item:view:own`、`item:edit:own`、`item:submit`、`item:withdraw`、`item:offline:own`、`item:publish`、`review:view`、`review:decide` | EDITOR 前 6 个本人范围码；REVIEWER `review:view`、`review:decide`、`item:publish` |
| `005-r2-category-permissions.sql` | 分类类 7 个：`category:view/add/edit/move/sort/enable/delete` | 仅 ADMIN（学生与审核员不自动获得分类写权限） |
| `006-r3-item-claim-permissions.sql` | 信息管理与认领类 8 个：`item:view:any`、`item:batch`、`item:offline:any`、`item:delete:any`、`item:delete:own`、`claim:create`、`claim:cancel:own`、`claim:handover:own` | EDITOR `item:delete:own` 与三个 claim 本人码；其余仅 ADMIN |

`com.team.cms.common.security.PermissionCodes` 中共有 41 个规划常量，上述迁移已发布其中大部分。
仍未被任何种子发布的码按团队约定**不自动授权**（`docs/r1-total-integration.md:22`）；需要新码时先向高鸣远登记编号，
再从 007 起新建迁移，不回写已发布的 003～006。统一命名的后续草案见 `docs/gjt-r4-permission-alignment-proposal.sql`。

### 3.4 声明式鉴权

```java
@GetMapping
@RequirePermission("user:manage")
public Result<PageResult<UserSummaryResponse>> page(@Valid @ModelAttribute UserQuery query) { ... }
```

- 未登录：拦截器抛 401。
- 已登录但无权限：切面抛 403。
- 注解值可以是字面量，也可以用 `PermissionCodes` / 模块内常量，避免拼写漂移。
- `IdentityContractAuditTest` 会静态校验「每个端点都声明了权限」以及「注解里的权限码真实存在」。

## 4 权限矩阵（当前 develop 实测）

- 401 = 未登录或会话失效；403 = 已登录但无权限；200 = 放行。
- `GET /api/public/categories`、`/api/public/items`、`/api/system/**`、`/api/auth/register`、`/api/auth/login` 无需登录。

| 接口 | 未登录 | EDITOR | REVIEWER | VIEWER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| `GET /api/auth/me` | 401 | 200 | 200 | 200 | 200 |
| `POST /api/auth/logout`、`GET /api/auth/csrf` | 401 | 200 | 200 | 200 | 200 |
| `GET /api/users`、`/api/users/{id}` | 401 | 403 | 403 | 403 | 200 |
| `PUT /api/users/{id}/status`、`/api/users/{id}/roles` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/roles`、`/api/roles/{id}` | 401 | 403 | 403 | 403 | 200 |
| `POST /api/roles`、`PUT /api/roles/{id}`、`PUT /api/roles/{id}/permissions` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/permissions` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/operation-logs` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/reviews`、`/api/reviews/history` | 401 | 403 | 200 | 403 | 200 |
| `GET /api/categories/tree`、`POST /api/categories` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/items/mine`、`POST /api/items` | 401 | 200 | 403 | 403 | 200 |
| `GET /api/items/{id}/review-records` | 401 | 403 | 200 | 403 | 200 |

矩阵由 `PermissionMatrixTest` 自动回归（夹具 `auth-schema-test.sql` 已含 003 与 004 种子）。
`005` 与 `006` 的差异只在分类与认领码上：分类管理与写接口仅 ADMIN 放行，认领相关接口
（`POST /api/items/{id}/claims`、`/api/claims/{id}/cancel`、`/api/items/{id}/resolve`）由
`claim:create`、`claim:cancel:own`、`claim:handover:own` 授权给 EDITOR，回归由杨程模块的
`ClaimFlowTest`、`ClaimWriteAccessTest`、`ConcurrentClaimApprovalTest` 覆盖，本矩阵不重复断言。

### 4.1 所有权规则

- 草稿与本人信息按 `authorId` 过滤，他人访问统一返回 404，不暴露是否存在。
- 审核记录接口 `GET /api/items/{itemId}/review-records` 没有 `@RequirePermission`，由
  `ReviewServiceImpl.records` 判断「作者本人或审核人员」，他人得到 403。
- 管理类接口不按所有权放行，只看权限码。

## 5 会话与 CSRF

### 5.1 会话

- 登录成功后旧会话 `invalidate`，再创建新会话写身份，防止会话固定攻击。
- Servlet 会话超时 30 分钟（`WebAppInitializer#onStartup` 中 `setSessionTimeout(30)`）。
- 会话里只保存当前用户快照；拦截器每次请求都用会话中的用户主键重新查库装载角色与权限，
  因此**停用账号、调整角色、撤销权限对既有会话立即生效**：停用后下一次请求就是 401，
  撤权后下一次请求就是 403，都不需要重新登录。
- 退出调用 `POST /api/auth/logout`，销毁会话并清理线程上下文；此后原会话访问任何受保护接口都是 401。
- 最后一个有效管理员不能被停用或降级，接口返回 409。

### 5.2 CSRF

- 登录成功后服务端换发 32 字节随机令牌，存在会话里；`GET /api/auth/csrf` 返回同一令牌。
- 所有写接口（POST/PUT/DELETE）要求请求头 `X-CSRF-Token` 与会话令牌一致，否则 403。
- 公开 api.js 的 `Cms.api.request` 会对非 GET 请求自动附加该头，页面无需手写。
- 令牌缺失、错误、或使用他人会话的令牌都会被拒绝，`PermissionMatrixTest` 与
  `UserRoleAdminTest` 都有对应用例。

## 6 排障

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 访问任何接口都是 401 | 未登录、会话超时、账号被停用、或已退出 | 重新登录；确认 `enabled=1` |
| 管理员也被 403 | 会话失效后又用旧令牌；或拦截器没生效 | 重新登录并取新令牌；确认 `SpringMvcConfig` 注册了拦截器 |
| 写接口 403 但读取正常 | 请求头缺 `X-CSRF-Token` | 先 `GET /api/auth/csrf`，再带上返回的字符串 |
| 越权测试返回 400 而不是 403 | 请求体不合法，参数校验先于权限切面执行 | 用合法请求体重试，再断言 403 |
| 启动日志「未配置 CMS_ADMIN_PASSWORD」 | 没有设置环境变量 | 设置后重启，或先用已有管理员登录 |
| 学生发不了信息，一直 403 | 数据库没有执行 004 迁移，或该账号不是 EDITOR | 按 003→004 顺序执行迁移；确认 `sys_role_permission` 中存在 `item:create` |
| 页面能打开但接口 401 | 用 `file://` 直接打开页面，cookie 不发送 | 通过 `/cms/pages/...` 访问 |

## 7 给其他模块的接入约定

1. 取用户：`CurrentUser user = CurrentUserContext.require();` 不要自己查会话。
2. 声明权限：`@RequirePermission(PermissionCodes.ITEM_EDIT_OWN)`，权限码优先用常量。
3. 写接口必须调用 `csrfTokenService.requireValid(request)`，否则前端虽然带令牌但后端不会校验。
4. 需要「本人限定」的接口，在 Service 里用 `require().getId()` 过滤，Controller 不做所有权判断。
5. 新增权限码时：先确认它属于已发布种子还是规划码，再决定是否要提迁移；不要直接改 003。
