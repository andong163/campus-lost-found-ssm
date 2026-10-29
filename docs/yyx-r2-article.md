# YYX R2 信息编辑与审核接入记录

## 实际接入

- 2026-10-25 在 `feature/yuanyunxuan-article` 检查到干净工作区，拉取并快进同步 `origin/develop`，保留 YYX R1 草稿成果。没有提交、推送或合并公共分支。
- 复用 R1 的 `Article`、`ArticleMapper`、会话身份、CSRF、分类校验、分页响应，以及总基线的审核记录和认领 Mapper。新增本人分页、全状态详情、编辑字段的条件更新。列表固定按 `created_at DESC, id DESC`，页码默认 1、每页 10、最大 100。
- 补齐尚未实现的 `ItemWorkflowService`：提审、撤回、下架、转草稿、审核批准/驳回、批准后发布。各写操作锁定信息行，比较客户端版本与当前状态；审核记录和状态更新在同一事务。下架前拦截已批准认领，并在同一事务关闭待处理认领。
- 补齐最小审核待办、待发布队列、决定与发布接口和页面；审核员从真实会话取得身份，不能审核本人信息，驳回必须有意见。作者可读取自己信息的审核记录。
- 页面入口：`/pages/index.html` → `/pages/auth/login.html` → `/pages/items/mine.html`；本人详情 `items/detail.html?id=...`、编辑 `items/edit.html?id=...`；审核员待办 `/pages/review/pending.html`。页面调用同源真实 API，并显示加载、空结果、错误、版本与状态，写操作按钮防重复提交。

## 接口与组员衔接

| 接口 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/items` | `item:create` | 会话作者创建草稿 |
| `GET /api/items/mine?page=1&size=10&status=DRAFT` | `item:view:own` | 只按会话作者查询，`PageResult<PrivateArticleDetail>` |
| `GET /api/items/{id}`、`GET /api/items/{id}/mine`、`GET /api/items/{id}/reviews` | `item:view:own` | 本人草稿、全状态详情、审核意见；其他作者信息返回 404 |
| `PUT /api/items/{id}` | `item:edit:own` | 可编辑字段加 `version`，只允许 DRAFT/REJECTED |
| `POST /api/items/{id}/submit` | `item:submit` | 请求 `{ "version": n }`；作者限定 |
| `POST /api/items/{id}/withdraw` | `item:withdraw` | 待审撤回 |
| `POST /api/items/{id}/offline` | `item:offline:own` | 已发布下架 |
| `POST /api/items/{id}/draft` | `item:edit:own` | 已下架转草稿 |
| `GET /api/reviews/pending`、`GET /api/reviews/approved` | `review:view`、`item:publish` | 待审与待发布分页 |
| `POST /api/reviews/{id}/approve|reject` | `review:decide` | `{ "version": n, "comment": "..." }`，驳回需意见 |
| `POST /api/reviews/{id}/publish` | `item:publish` | 仅 APPROVED 到 PUBLISHED |

所有写请求需同会话 `X-CSRF-Token`。状态或版本冲突返回 409。第二轮接口已使用共享 `PermissionCodes`。部署现有数据库时，在已发布的 `003-auth-permission-seed.sql` 之后执行新增的幂等 `004-item-review-permissions.sql`，再重新登录以加载新增授权。003 和其他仍使用旧编码的模块保持不变；后续由郭金滔/高鸣远统一规划旧授权的淘汰时机。`src/test/resources/auth-schema-test.sql` 已镜像这组新增授权。

杨程后续接管工作流时，以当前 `ItemWorkflowService` 和 `ArticleMapper` 条件更新为单一状态入口扩展；不要另建同名实现。认领规则继续由现有 `ClaimRequestMapper` 提供：本轮只使用批准计数和下架关闭待审申请。后续认领核实与交接不在本轮范围。

## 验证与过程

- 隔离 H2 的 `ArticleDraftHttpTest` 增加正常编辑→提审→驳回→修改重提→批准→发布、本人隔离、旧版本、待审编辑冲突、自审回避、撤回、下架转草稿、细分授权撤销，以及审核记录写失败的事务回滚检查。
- 2026-10-25 使用 `YyxR2IsolatedMySql` 在本机 MySQL 创建独立的 `cms_yyx_r2_eb8e7a64` 测试库，执行 `init.sql`、003 和 004，生成仅限该库的测试账号与分类。Chrome 页面联调实际完成登录→本人草稿列表→详情→编辑→提审→审核员驳回→作者查看意见→修改重提→审核员批准→待发布队列发布→作者下架→转草稿；状态筛选也观察到正确的空列表。页面操作经本地 Tomcat 9 WAR 与该 MySQL 库完成，没有修改 `cms_content` 业务数据。验收后已停止本地 Tomcat，并删除该测试库及临时连接配置。
- 首次测试中回滚夹具使用了不存在的分类，审核前返回 404；补上有效分类后受影响测试通过。页面内脚本及共享脚本通过 `node --check`。最终 `mvn -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -B -ntp verify` 通过：77 个单元/HTTP 测试、1 个 WAR 部署集成测试，产物为 `target/cms.war`。受限沙箱曾禁止 Tomcat 绑定本机端口，允许本机端口后测试通过。
- 本轮代码、页面、测试和此记录由 AI 辅助生成、执行并完成浏览器验收；没有声称人工完成。
