# 身份模块测试记录与问题修复（GJT R4）

记录时间：第四轮，第三次合并（`fba0bb6` 完成五人第三轮总合并）之后。
执行环境：Windows 11、JDK 21（IntelliJ JBR，源码与字节码级别为 11）、Maven 3.9.16、
H2 2.2.224（测试库）、MySQL 8（生产库脚本）。

## 1 执行方式

```powershell
$env:JAVA_HOME="D:\javaweb\IntelliJ IDEA 2025.3.3\jbr"   # 本机实际路径
$env:MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dmaven.repo.local=D:/java框架课程设计/.cache/m2"
mvn -o test -DargLine="--add-opens=java.base/java.lang=ALL-UNNAMED"
```

`--add-opens` 只在 JDK 17 及以上跑 `ContainerSmokeTest`（真实 Tomcat 9 部署）时需要，
JDK 11 下不需要。`-o` 为离线模式，依赖已缓存在本地仓库。

## 2 结果汇总

| 范围 | 用例数 | 结果 |
| --- | --- | --- |
| 全量（`mvn -o test`） | 159 | 全部通过，BUILD SUCCESS |
| 其中本轮新增 | 16 | 全部通过 |
| `PermissionMatrixTest`（权限矩阵回归） | 11 | 通过 |
| `IdentityContractAuditTest`（身份契约审计） | 5 | 通过 |
| 既有用例（框架、队友模块） | 143 | 无回归 |

既有 143 个用例覆盖框架集成、Web 契约、容器冒烟、文章草稿与管理、分类、认领、审核工作流、
审计、R1 兼容性等，本轮没有修改它们的断言。

## 3 权限矩阵核对结论

矩阵按「公开 / 学生 / 审核员 / 只读 / 管理员」逐行核对，结果见
`docs/gjt-r4-identity-guide.md` 第 4 节。核对方式不是人工点页面，而是
`PermissionMatrixTest` 为每个角色真实登录后调用受保护接口并断言 HTTP 状态码，
因此角色种子或注解一改，测试就会失败。

核对覆盖的维度：

- 合法访问：管理员可进每个受保护接口，审核员可进审核工作台，学生可创建并查看本人信息，
  只读用户可调用自身身份接口。
- 未登录：8 个受保护接口 + 分类写接口全部 401，证明拦截器不能绕过。
- 无权限：学生访问用户或角色管理、审核员访问用户管理、只读用户越界，全部 403。
- 越权：学生替自己或他人分配角色返回 403；访问他人审核记录返回 403。
- 停用：账号停用后既有会话下一次请求返回 401。
- 撤权：撤销角色后同一会话立即返回 403，无需重新登录。
- 管理员可管理性：最后一个有效管理员不能被停用或降级，返回 409。
- 隐私：响应体不含 `password`、`passwordHash` 字段。

## 4 真实问题与修复过程

### 4.1 参数校验先于权限切面，越权用例最初得到 400

初次编写「学生替自己提权」用例时，请求体不合法，接口返回 400 而不是期望的 403。
排查后确认执行顺序是：Spring MVC 参数绑定与 `@Valid` 校验在 HandlerMethod 入参解析阶段完成，
而权限切面是在方法调用时才织入，所以请求体不合法时校验先抛异常。

处理：越权用例一律携带合法请求体，再断言 403。该结论同时写入操作说明第 6 节排障表和
用例注释，避免其他同学误判「越权没生效」。

### 4.2 规划权限码未授权导致业务接口 403（已在 004～006 迁移中解决）

R4 编码时的 develop 还只执行过 003，`item:create`、`category:view`、`review:decide` 等规划码
没有对应授权，学生与审核员在业务接口上一律 403。团队约定的处理方式是**新建迁移而不是回写 003**
（`docs/r1-total-integration.md:22`）。

第三次合并后 004～006 已随队友模块发布：

- `004-item-review-permissions.sql`：item/review 共 9 个码，EDITOR 得到 6 个本人范围码；
- `005-r2-category-permissions.sql`：分类 7 个码，仅 ADMIN；
- `006-r3-item-claim-permissions.sql`：信息管理与认领 8 个码，EDITOR 得到 `item:delete:own` 与三个 claim 本人码。

处理：把矩阵里「学生一律 403」的旧结论改成实测值，并新增用例
`studentReachesOwnItemEndpoints`（学生 `GET /api/items/mine`、`POST /api/items` 均 200，
但仍访问不了 `/api/categories/tree` 与 `/api/users`），用测试把现状钉住，防止文档与实现再次漂移。

### 4.3 静态审计按数量放行，掩盖了三个新模块的裸端点

第三轮总合并后队友新增了 `PublicArticleController`、`ArticleManagementController`、
`ClaimController`，审计第一版用「端点总数 − 注解数 = 白名单数」的按数量豁免，
合并后立刻失败并提示 7 个端点未声明权限。

逐个核实后发现这三处都是**在 Service 内做所有权判定**而非注解判定：

- `PublicArticleController.list/detail`：公开检索，路径已进登录白名单；
- `ArticleManagementController.delete`：`ItemWorkflowServiceImpl.delete` 内按作者本人 +
  `item:delete:own` / `item:delete:any` 判定；
- `ClaimController` 的 `mine/forItem/handover/foundContact`：`ClaimReadPolicyImpl` 与
  `ClaimServiceImpl` 内按发布者本人 / 已核实申请人 / 处理角色判定。

处理：把按数量豁免改成**按方法名逐条登记理由**（`ALLOWED` 映射），并新增
`allowedMethodsStillExist()` 校验登记的方法仍然存在，避免白名单腐化后继续掩盖新增裸端点。
同时把 `seededCodes()` 改为扫描 `sql/init.sql` 与 `sql/migrations/*.sql` 里所有
`INSERT INTO sys_permission` 语句块，而不是只看 003。

### 4.4 端点解析正则漏掉了带路径参数的方法

修审计时发现 `forItem`、`detail`、`delete`、`records`、`handover`、`foundContact` 被漏检：
原实现用 `text.indexOf('{', matcher.end())` 找方法体，而 `@GetMapping("/items/{itemId}/review-records")`
的路径里自带花括号，会提前命中。

处理：新增 `skipAnnotationArguments()`，从注解名之后跳过空白与整个参数括号（含字符串与转义），
再从方法体左花括号开始解析。

### 4.5 两套审核入口并存

`/api/reviews`、`/api/reviews/history`、`/api/reviews/{itemId}/decision` 使用
`ReviewPermissions.REVIEW_HANDLE`；`/api/reviews/pending`、`/api/reviews/approved`、
`/api/reviews/{id}/approve`、`/reject`、`/publish` 使用 `REVIEW_VIEW`、`REVIEW_DECIDE`、`ITEM_PUBLISH`。

004 发布后这两组码都授给了 REVIEWER，所以**权限口径已经一致**，剩下的问题是功能重复：
同一个业务有两套语义重叠的入口。本轮不删除队友的接口，只在审计测试的豁免清单里登记，
并在操作说明里注明「审核员日常走 `/api/reviews` 这一组」。统一入口由杨程在后续轮次收口。

### 4.6 停用与撤权对既有会话的即时生效

第一轮实现时曾把完整用户快照放进会话，导致停用或撤权后旧会话仍可用。
第二轮调整方案：会话只保存当前用户快照用于识别，拦截器每次请求都用其中的用户主键
重新查库装载角色与权限并覆盖会话属性。第四轮用两个用例固定该行为——
`disablingAccountInvalidatesExistingSession`（停用后 401）与
`revokingRoleTakesEffectOnExistingSession`（撤权后 403），防止以后有人为了性能又改回缓存权限。

### 4.7 最后管理员保护

停用或降级最后一个有效管理员时返回 409，而不是 200。用例
`lastAdministratorCannotBeDisabledOrDowngraded` 同时覆盖两条路径，
避免系统被锁死在无人可管理的状态。

## 5 未覆盖与遗留

| 项 | 说明 | 归属 |
| --- | --- | --- |
| 分类写权限 | `005` 只授 ADMIN，学生与审核员不能用分类管理接口（有意为之） | 孙睿定默认授权 |
| 两套审核入口 | 权限已一致，功能重复，待统一 | 杨程收口 |
| 认领接口矩阵 | 由 `006` 授权，回归在 `ClaimR2IntegrationTest`、`ClaimFlowTest`，本轮不重复断言 | 杨程 |
| 改密接口 | 当前没有自助改密，管理员口令只能通过环境变量初始化 | 后续轮次 |
| 并发与性能 | 本轮未做压力测试 | 孙睿 R4 负责性能证据 |
| 真实 MySQL 双跑 | 测试全部跑在 H2；生产脚本未在本轮自动执行 | 高鸣远空库安装验收时覆盖 |

## 6 复现步骤

1. 按 `docs/gjt-r4-identity-guide.md` 第 2 节准备数据库并设置管理员口令。
2. 依次执行 `sql/init.sql`、`sql/migrations/003-auth-permission-seed.sql`、
   `004-item-review-permissions.sql`、`005-r2-category-permissions.sql`、
   `006-r3-item-claim-permissions.sql`。
3. `mvn -o test`，确认 159 个用例通过。
4. 导入 `docs/gjt-r4-apifox-cases.json`，先登录管理员、再取 `X-CSRF-Token`，
   按文件夹顺序执行，核对状态码与本文件第 3 节一致。
5. 浏览器打开 `/cms/pages/auth/login.html`，用管理员登录，核对用户与角色页面入口
   与 `menu.json` 声明一致。
