# YYX R4 联系方式与信息端回归接入记录

初次实施日期：2026-11-03。工作分支：`feature/yuanyunxuan-article`。以下保留当时的实施与验证记录；最新交付决定见文末。

## 同步结果及前端现状

开始时 HEAD 为 `200a0d2`，工作区干净，没有已修改或未跟踪文件需要 stash。先执行 `git fetch origin`，再刷新远程所有分支引用，以远程实际提交为准。最新 `origin/develop` 是 `f64dc0c`，包含第二轮总合并 `c3f8562` 以及郭金滔第三轮身份页面和写操作 CSRF `1a5c5aa`，没有把全组第三轮标为完成。

执行 `git merge --no-commit --no-ff origin/develop`，自动合并成功，无冲突。为同时遵守“合入最新 develop”和“先不要提交”，没有生成合并提交，`MERGE_HEAD` 保留为 `f64dc0c`，HEAD 仍为 `200a0d2`。同步引入的身份模块文件由 Git 暂存，R4 修改和新增文件保留在工作区；没有额外执行 git add，也没有重写历史。不要把暂存区中的郭金滔成果误认为本轮新写代码。原 R3 的公开检索和批量功能完整保留。

高鸣远远程分支最新为 `origin/feature/gaomingyuan-base` 的 `db41d6b`，它已经是 develop 的祖先；相对 develop 没有额外未合入提交。远程可见的首页、布局、样式、Session 外壳、fetch、分页组件均已包含并复用。该分支实际交付的是公共前端和第二轮首页，不能据此声称全套业务页面均已交付，也无法确认他尚未推送的本地版本。没有合入他的个人分支，没有另建前端工程。

杨程的 `origin/feature/yangcheng-review-claim` 最新 `17b9a97` 已实现真实认领服务和联系方式策略，但尚未进入 develop。按本轮依赖范围读取、复用其新增 claim Java 文件、`/claim/mine.html`、`/claim/manage.html` 和相关测试，没有合入整条分支。原审核、信息状态机、实体、Mapper 和 SQL 契约均保持现有实现。

## 本轮接口及规则

接口基础地址仍为 `/cms/api`，统一返回 `code/message/data`，身份使用现有 AuthInterceptor 从真实 Session 每次重载，写操作使用现有 CSRF 校验。未引入 Spring Boot，保持 JDK 11、Spring MVC、MyBatis、WAR 和 Tomcat 9。

| 方法与路径 | 实际行为 |
| --- | --- |
| `GET /items/{id}/contact` | 单条联系方式。LOST 普通访问者须具有 `item:contact:lost`，且信息已发布、OPEN、未删除、分类及祖先有效；作者和有审核处理权限者按私有访问边界读取。FOUND 调用统一 `ClaimReadPolicy`，仅作者、核实通过者和有处理权限人员可读；已完成认领保留授权历史读取。 |
| `GET /items/{id}/r4-detail` | 有 `item:view:own` 权限的作者详情摘要，不含联系方式。本人详情页改用此新增投影，避免打开详情时提前取得联系字段；原编辑和本人查询接口契约不变。 |
| `GET /items/{id}/claim-eligibility` | 真实登录后的资格查询，只返回 `eligible/reason`。非作者不能借此枚举未公开或停用分类的信息。规则与实际申请共用 `ClaimReadPolicy`，前端不自行判断授权。 |
| `GET /public/items/{id}/resolution` | 按公开可见范围返回处理状态、结案时间、结果码；没有联系方式、身份、证据或审核意见。已下架、删除、分类失效均不公开。 |
| `GET /items/{id}/resolution` | 作者的同结构历史查询，允许查看本人已下架或停用分类下的历史。 |

联系接口成功、401、403、404 等响应均经过先于认证执行的禁止缓存拦截器，设置 `Cache-Control: no-store, private, max-age=0`、`Pragma: no-cache`、过期时间。该拦截器同时覆盖新增的私有详情、认领、交接上下文和资格读取。

正常和越权联系读取经现有 AuditTemplate/AuditService 记录操作者、目标、固定动作、SUCCESS/FAILURE、requestId、时间与耗时；匿名/失效会话在拦截器完成阶段记录 FAILURE，操作者为空。审计对象不接收联系方式、证据、正文或审核意见。沿用团队审计写入故障只记录安全告警的契约，因此没有把审计服务可用性保证描述成强制事务成功。

结案结果为 `OPEN`、`LOST_RECOVERED`、`FOUND_HANDOVER`；FOUND 必须查询到 COMPLETED 历史才展示“已完成实际交接”。若旧记录只有 RESOLVED 而没有 COMPLETED，返回 `RESOLVED_UNCONFIRMED`，页面明确提示历史待核对，不把批准当成交接。

## 复用依赖和维护归属

| 模块 | 来源与接入方式 | 后续维护 |
| --- | --- | --- |
| 认领申请、列表、核实、撤销及交接上下文 | 复用杨程 `17b9a97` 的 ClaimService/DTO/Controller。新增接口加入现有 CSRF 和操作权限校验；只在隔离测试中注入测试身份。 | 杨程主责；整合时按提交来源逐文件比较，避免再次引入重复实现。 |
| FOUND 联系授权与认领资格 | 复用 ClaimReadPolicy，兼容新增 `eligibility`、`requireEligible` 和已加载 Article 的读取重载。Article 模块只调用策略。COMPLETED 认领人保留其已核实的历史联系资格。 | 杨程维护统一规则；袁运轩维护信息端调用。 |
| LOST 找回、FOUND 交接状态操作 | 调用已经合并的 `review.service.ItemWorkflowService.resolve`，没有改写其实现。锁顺序仍为物品行后申请行，状态、版本、COMPLETED、RESOLVED 和关闭其余 PENDING 使用原事务。 | 杨程。 |
| 联系/历史/非敏感详情投影与页面接入 | 本轮新增 ArticleR4Controller、ArticleR4Service、专用 DTO、ContactResponseInterceptor 及两个 JS 接入文件。 | 袁运轩。 |
| 权限元数据 | 新增 `006-yyx-r4-contact-claim-permissions.sql` 提案，追加 `item:contact:lost`、`claim:create`、`claim:view:own`、`claim:cancel:own`、`item:resolve:own`、`claim:handover:own`；EDITOR 默认获得六项本人权限；ADMIN 仅默认获得 LOST 联系与本人找回，未给 ADMIN/VIEWER/REVIEWER 自动增加认领申请、撤销或交接权限。 | 高鸣远确认编号和部署整合；业务权限归身份/认领负责人协同维护。 |

006 在本次刷新后的远程编号表中空闲，登记为待整合提案；不回写 003～005，不改变生产表结构，不改 init.sql 总稿。新库执行 init.sql 后也须执行 006，旧库在已有 003～005 后执行 006。自动测试验证脚本重复执行；本次没有升级、清空或重建真实业务库。既有认领表的两类唯一约束继续使用。

复用的接口保持杨程路径：`POST /items/{id}/claims`、`GET /claims/mine`、`GET /claims`、`GET /items/{id}/claims`、`POST /claims/{id}/decision`、`POST /claims/{id}/cancel`、`POST /items/{id}/resolve`、`GET /items/{id}/handover`。兼容 `/items/{id}/found-contact` 也使用统一策略、审计和无缓存；新信息详情统一调用 `/contact`，不同时请求两者。

## 页面入口

- `/cms/pages/items/public-detail.html?id=<实际物品ID>`：保留原详情布局，增加主动查看联系、真实资格查询、认领链接与结案结果/时间。已结案没有新认领链接。
- `/cms/pages/items/detail.html?id=<本人ID>`：保留原信息操作和审核意见，新增主动查看联系及实际找回/交接按钮。无 APPROVED 认领时只提示不能交接；核实批准本身不结案。
- `/cms/claim/mine.html?itemId=<实际物品ID>`：复用杨程申请表、列表、撤销和历史，带入物品 ID。
- `/cms/claim/manage.html`：复用杨程核实队列。两页接入高鸣远的外壳、统一 fetch、CSRF 和 Pagination，使用原有安全文本工具；没有另建一套页面。

新增动态文本全部使用 textContent。会话切换、退出或 pagehide 清理联系和证据区域，晚到的私有响应不回填；认领提交有进行中保护。没有将联系方式放进隐藏字段，也没有通过前端按钮显隐替代后端校验。

## 实际验证

自动验证由 AI 运行，不等同于袁运轩人工逐行审查。

| 验证 | 结果与范围 |
| --- | --- |
| 第一批受影响测试 | 38 项通过：信息端、旧工作流回滚、杨程认领和并发批准。 |
| R4 HTTP 用例 | 新增 ArticleR4HttpTest 共 7 项，真实注册/登录、即时撤权/停用、LOST 范围、FOUND 作者/普通人/核实人/审核员、匿名与拒绝审计、无缓存、非敏感摘要、公开响应、结案限制、CSRF、版本冲突、交接失败回滚、寻物找回。使用独立 H2，等价生成列和唯一索引，不接触业务库。 |
| 最近一次受影响复跑 | 因新增非敏感摘要和统一策略重载而复跑 18 项，全部通过。之后的最终构建覆盖最终版本。 |
| JavaScript | 21 项通过，其中原公共组件 16 项，新 R4 5 项：主动点击前零联系请求、纯文本显示、结案禁认领及时间、匿名登录引导、退出后晚到响应拦截、游客登录刷新与身份隔离。新增及接入页面脚本语法检查通过。 |
| Maven verify 及必要复验 | 首次 134 项测试 + 1 项 WAR 部署测试通过，50.802 秒。随后范围复核发现新增权限脚本给 ADMIN 默认授予认领操作超出规格，收紧并补断言后才再次运行。最终版本仍为 134 + 1 项，失败/错误/跳过均为 0，BUILD SUCCESS，53.837 秒；JDK 11，包含实际 Tomcat 9 HTTP 冒烟及构建 WAR 的隔离父类路径部署测试。 |
| 浏览器 | 临时 Tomcat 9 + 独立 H2，Chrome 确认匿名点击登录、未核实学生联系拒绝、携带物品 ID 跳转、提交后 PENDING、私有记录退出清空、审核队列实际读取、作者主动读取联系、无核实通过申请时不出现交接按钮、LOST 作者找回按钮。标题/正文/地点的 HTML 测试字符串显示为文字。 |

浏览器核实意见的原生 prompt 触发后，自动化出现 CDP 焦点超时，未确认该次核实提交。新标签页恢复后继续验证了作者详情；未把“浏览器中完成核实与最终结案确认”记为通过。完整结案、回滚和历史结果由上述 HTTP/JS 自动测试验证，仍需真人完成下方操作。

本地原始日志在 `/private/tmp/yyx-r4-affected.log`、`yyx-r4-http.log`、`yyx-r4-final-affected.log`、`yyx-r4-js.log`、`yyx-r4-final-build.log`、`yyx-r4-final-build-after-permission-fix.log`。原始测试日志只用于本地排错，含合成测试数据，不复制到 Git。正式交付包为 `target/cms.war`；临时浏览器夹具位于 /private/tmp，不进入 WAR 或源码提交；验证后已关闭测试标签页和临时 Tomcat，H2 数据随进程释放。正式 WAR 检查未发现私密本地配置、H2/JUnit、测试类或 Boot 依赖。

信息端性能复用原查询：公开列表仍为原 R3 批量查询和分类树逻辑，未新增列表逐条查询。新接口均为单物品读取：FOUND 联系复用已加载物品，最多一次资格计数和一次审计写入；结案查询最多一次 COMPLETED 计数；资格查询一次分类有效性读取及至多两次计数。公开详情至多新增历史与资格两个请求，联系仅点击后请求；登录不会重复读取公开历史。申请事务内保留分类绑定锁校验，这是并发检查所需。没有新增 N+1、逐条用户/分类查询或压测体系；未声称有本轮吞吐量、EXPLAIN 或真实 MySQL 性能测量。

## 交给高鸣远核对的既有问题与剩余事项

1. `pages/items/public.html` 的原 R3 首屏脚本：api.js 为 defer，底部 inline 初始化立即使用 Cms。静态核查显示存在初始化顺序问题，可能先显示分类/列表加载失败，脚本加载后手动查询仍可重试。该文件来自原 HEAD `200a0d2`，本轮未改；交高鸣远复现和协调处理，不将其记为本轮已修复。原公开 API 的隐私检查通过，不代表该旧页面首屏已通过浏览器验收。
2. 当前公共 shell 的 module.json 只接受 `pages/<module>/...`，而杨程既有认领路由是 `/claim/...`，claims 清单仍为空，侧栏显示待开放。详情认领入口和上述直接地址可用。本轮没有扩大公共路由规则或新建重复页面；由高鸣远/杨程确认最终路由后统一接入导航。
3. 远程没有发现高鸣远在 db41d6b 之后的额外完整前端提交；若其本地另有未推送版本，待其交付后比较本轮两处详情和认领页面接入，不能把未知版本当成已同步。
4. 006 编号/安装路径、杨程 17b9a97 与本轮适配的逐文件整合待团队确认。当前不向任何人发送消息，也没有创建交接 PR；本记录是待交接清单。
5. 本轮限定检查未发现现有公开响应泄露联系方式或证据；新增公开投影测试通过。没有宣称全站、全历史接口或真实业务库的隐私验收全部通过。
6. 真实 MySQL 和外部 Tomcat 上的 R4 全流程、原生确认弹窗、完整前端联调仍待人工核查。没有扩展管理员例外关闭等未进入本轮入口的操作，也没有代修已合并业务。

## 启动与演示

已有环境先由数据库维护人按原升级顺序执行 006 并重新登录。保留个人 jdbc-local.properties；不把它加入 Git。构建使用本机已有 JDK 11：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -B -ntp verify
```

正式部署将 `target/cms.war` 放入已有 Tomcat 9 的 webapps。Mac 本地演示可继续用仓库现有 LocalServer，父类路径只带测试启动器、Tomcat 和注解 API，业务仍从构建的 WAR 加载：

```bash
YYX_RUN_CP="target/test-classes:.cache/m2/org/apache/tomcat/embed/tomcat-embed-core/9.0.122/tomcat-embed-core-9.0.122.jar:.cache/m2/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2.jar"
"$JAVA_HOME/bin/java" --add-opens=java.base/java.lang=ALL-UNNAMED -Dfile.encoding=UTF-8 -Dcms.port=8088 -cp "$YYX_RUN_CP" com.team.cms.tools.LocalServer
```

访问 `http://127.0.0.1:8088/cms/pages/index.html`，再进入上述页面，ID 必须使用本人测试数据的实际返回值。公开列表原首屏问题尚待处理，演示 R4 可直接打开实际物品详情。认领管理使用现有有权限的审核账号，不写死运行时用户 ID，也不提供通用测试身份后门。

## AI 辅助与个人需求调整

下表前五行依据袁运轩在本次任务中提供的真实需求审阅记录整理；没有补造更早的提示词原文或时间，也没有把需求审阅写成本人改过代码。实现和自动检查由 AI 执行，人工核查尚待反馈。

| AI 初始建议 | 我发现的问题 | 我的调整要求 | 实际实施结果 |
| --- | --- | --- | --- |
| 直接推进第四轮 | 我意识到此前未主动同步团队成果 | 先同步最新 develop，以远程实际情况为准 | 已 fetch 到 f64dc0c，无冲突合入工作区；按本次不提交要求保留未提交合并。 |
| 自行推进页面接入 | 我得知高鸣远已有前端，担心重复开发与冲突 | 复用其页面和公共组件 | 已确认远程 db41d6b 全部在 develop，复用公共布局、样式、Session、fetch、分页；补充说明远程尚无全套业务前端，认领页复用杨程已推送成果。 |
| 包含前三轮整合和历史问题修复 | 我认为超出个人任务范围 | 保留已合并业务，历史 Bug 交高鸣远 | 原工作流和 Mapper 未重写；旧公开列表初始化及导航接入列入待交接项。 |
| 按缺少依赖继续扩展方案 | 我担心再次重复建设 | 优先复用已有真实组件，只补 R4 必要能力 | 复用杨程未合并的真实认领组件及已合并结案工作流；仅新增必要的权限、资格、联系和历史投影，登记来源与归属。 |
| 扩展较多验证 | 我希望控制开发范围 | 保留必要测试与一次最终构建 | 运行受影响测试、新 R4 HTTP/JS、有限浏览器交互及最终 verify；最后发现新增权限脚本范围问题后修正并做必要复验，没有全项目历史问题排查或新增压测。 |

实际实施中另有三个可追溯调整：AI 接入代码最初在游客登录后没有重新加载认领队列，随后新增会话初始化与切换处理，并用 JS 用例验证；读取 FOUND 联系时最初重复查询物品，随后增加策略重载复用已加载对象。最终范围复核还收紧了新增权限种子对 ADMIN 的默认授权，并增加角色授权断言。它们是 AI 本轮代码的修正，不是袁运轩人工定位或修改代码的记录。

待袁运轩实际核查，未完成前不勾选：

- [ ] 用真实测试账号打开公开详情和本人详情，在 Network 确认点击前没有 `/contact` 请求，点击后仅请求一次；检查无缓存响应头。
- [ ] 学生申请 → 审核员在 `/claim/manage.html` 核实 → 确认仍未结案 → 作者在本人详情确认实际交接；另一个学生不能再申请，历史应展示交接及时间。
- [ ] LOST 作者确认已找回，游客仍能看符合公开条件的结案历史；普通学生无法再读取已结案 LOST 联系方式。
- [ ] 退出或换账号，联系/证据立即清空；用包含尖括号的测试标题、地点、意见确认只显示文字。

收到真实操作结果后再补入“人工已完成”记录。本次没有用自动测试通过替代人工核查。

## 2026-11-09 本地提交授权与复核

重新获取远程引用并查询 GitHub：第三轮 PR #8 仍为 OPEN，`merged=false`，`origin/develop` 仍是 `f64dc0c`。向用户说明实际状态后，用户明确要求“直接提交第四轮吧”，选择先做本地提交，不再等待第三轮合并；本次不推送、不创建 PR，也不合并公共分支。

先将此前已暂存的郭金滔第三轮同步结果完成为独立合并提交，保留其原提交来源；然后单独提交本地第四轮代码及此前已完成的信息筛选界面、日期筛选、公开侧栏入口和地点提示调整。认领依赖的来源和维护归属仍按上文登记。由于个人第三轮 PR 尚未合并，后续推送前必须重新核对该 PR，避免把第四轮混入第三轮交付。

本次提交前重新运行 JavaScript 用例共 29 项，全部通过；公共脚本和信息页面内联脚本语法检查、暂存区及工作区的 `git diff --check` 均通过。首次 Maven 检查因沙箱禁止本地监听导致 ContainerSmokeTest 出错；获准在沙箱外复跑后，134 项测试及 1 项 WAR 部署测试全部通过，失败、错误、跳过均为 0，`BUILD SUCCESS`。复跑日志为本机临时文件 `/private/tmp/yyx-r4-precommit-verify-unrestricted.log`，不加入 Git。既有公开页初始化错误、日期绑定 400、导航接入和真实 MySQL 人工验收等限制继续保留，未因本地提交而标记完成。
