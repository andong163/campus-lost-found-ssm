# GJT R3：身份模块页面与公共布局接入

郭金滔负责的身份模块前端部分。本轮目标：登录、注册、用户管理、角色权限管理可以真正点开使用，
并且**复用高鸣远第二轮交付的公共布局、导航、会话状态、同源请求与分页组件**，不另行实现第二套请求层。

## 页面入口

| 页面 | 地址 | 需要的权限 |
| --- | --- | --- |
| 登录 / 注册 | `pages/auth/login.html` | 无需登录 |
| 用户管理 | `pages/auth/users.html` | `user:manage` |
| 角色权限 | `pages/auth/roles.html` | `role:manage` |

地址前缀是 WAR 上下文 `/cms`，例如 `http://localhost:8088/cms/pages/auth/users.html`。
页面内的链接一律由 `Cms.shell.link()` 计算，换上下文不需要改代码。

## 文件结构

```text
src/main/webapp/
├── assets/css/auth.css            身份模块自有样式（只新增，不改 base.css / modules.css）
├── assets/js/auth/login.js        登录 / 注册逻辑
├── assets/js/auth/users.js        用户分页查询、启停、角色分配、详情
├── assets/js/auth/roles.js        角色列表、新建、改名、权限勾选
└── pages/auth/
    ├── module.json                本模块入口清单（公共 shell 唯一读取来源）
    ├── login.html
    ├── users.html
    └── roles.html
```

公共组件位于 `assets/js/api.js`、`assets/js/shell.js`、`assets/js/pagination.js` 与
`assets/css/base.css`、`assets/css/modules.css`，由高鸣远维护，本模块不修改。

## 页面骨架（与公共布局一致）

```html
<link rel="stylesheet" href="../../assets/css/base.css">
<link rel="stylesheet" href="../../assets/css/modules.css">
<link rel="stylesheet" href="../../assets/css/auth.css">
<script defer src="../../assets/js/api.js"></script>
<script defer src="../../assets/js/pagination.js"></script>
<script defer src="../../assets/js/shell.js"></script>
<script defer src="../../assets/js/auth/users.js"></script>
<!-- body -->
<div data-cms-shell></div>
<main>本模块内容</main>
```

`body` 需要 `class="module-page"` 与 `data-page-title="…"`（shell 用后者渲染页头标题）。
模块脚本在 `shell.js` 之后执行，不需要手工调用挂载；私有内容要等 `await Cms.shell.ready` 之后再加载。

## 三个关键约定

### 1. 入口清单与权限一致

`pages/auth/module.json` 是唯一数据源，格式与 shell 约定一致：

```json
{ "label": "用户管理", "page": "pages/auth/users.html", "permission": "user:manage" }
```

shell 会按 `permission` 过滤导航项，无权限的入口不渲染；但**导航隐藏不是授权**——
`AuthPageContractTest` 会机械校验：入口声明的权限码必须真的出现在某个 `@RequirePermission` 注解上、
且存在于 SQL 权限种子中；入口路径必须满足 shell 的路径规则且文件真实存在。

### 2. 请求一律走公共封装

页面与模块脚本只使用 `Cms.api`：

```js
const result = await Cms.api.request('/api/users?page=1&size=10');
await Cms.api.request('/api/users/' + id + '/status', { method: 'PUT', body: { enabled: false } });
```

- 直接返回 `Result.data`，失败抛 `Cms.api.ApiError`（中文提示，不暴露 SQL/HTML/堆栈）；
- 非安全方法自动附带 `X-CSRF-Token`（登录、注册除外）；
- 401 派发 `cms:unauthorized`，shell 清空会话并弹出登录；
- 取消请求保持静默，不会把主动切页显示成故障。

页面中不出现 `fetch(`，`AuthPageContractTest` 会断言这一点。

### 3. 权限显示与接口校验双保险

- 客户端：`Cms.shell.hasPermission('user:manage')` 决定是否渲染私有内容，`ADMIN` 角色短路放行；
- 服务端：`@RequirePermission` + AOP 在真正执行接口时再次校验，手工构造请求绕不过去；
- 会话侧：拦截器每次请求按用户主键重新装载角色与权限，停用账号或撤销权限立即生效。

页面上的「没有权限」提示只是体验层，不是安全边界。

## CSRF

登录成功后服务端轮换会话令牌。公共 `api.js` 在第一次写请求前通过 `GET /api/auth/csrf` 取令牌并缓存，
写请求自动附带；遇到 403 会丢弃缓存。用户管理与角色权限管理的全部写接口
（`PUT /api/users/{id}/status`、`PUT /api/users/{id}/roles`、`POST /api/roles`、
`PUT /api/roles/{id}`、`PUT /api/roles/{id}/permissions`）在服务端调用
`CsrfTokenService.requireValid` 校验，缺失、错误或使用其他会话的令牌都会被拒绝。

## 与公共布局的关系

- 本轮只在 `pages/auth/` 与 `assets/css/auth.css`、`assets/js/auth/` 内新增文件；
- **没有修改** `pages/index.html`、`assets/css/base.css`、`assets/js/shell.js` 等公共文件（归高鸣远维护）；
- 顶栏、侧边导航、登录状态区域全部由 shell 渲染，本模块只填充 `<main>`；
- 退出登录、会话失效时监听 `cms:authchange`，清空私有 DOM。

## 验证方式

```powershell
$env:JAVA_HOME="D:\javaweb\IntelliJ IDEA 2025.3.3\jbr"
$env:MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dmaven.repo.local=D:/java框架课程设计/.cache/m2"
& "D:\java3\apache-maven-3.9.16\bin\mvn.cmd" -o test -DargLine=--add-opens=java.base/java.lang=ALL-UNNAMED
```

- `UserRoleAdminTest`：用户分页/启停/角色分配、会话即时生效、最后一个管理员保护、密码哈希不外泄、CSRF 拒绝、页面与静态资源可访问；
- `AuthPageContractTest`：入口清单格式、权限码与服务端注解/种子的一致性、页面是否接入公共组件。
