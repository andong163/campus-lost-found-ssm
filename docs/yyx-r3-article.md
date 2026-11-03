# YYX R3 公开检索与批量管理接入记录

2026-11-01 在 `feature/yuanyunxuan-article` 上确认工作区干净，`git fetch origin` 后快进同步 `origin/develop` 至 `c3f8562`。未合入其他组员尚未进入 develop 的分支，未提交、推送或创建 PR。

## 已接入

- 游客 `GET /api/public/items`、`GET /api/public/items/{id}`：仅查询未删除、PUBLISHED 且分类和全部祖先有效的信息。详情同样执行公开过滤；公开 DTO 只含标题、纯文本描述、类型、分类名称及路径、地点、事发及发布时间和处理状态。SQL 不读取联系方式；认领证据不在 Article 表或 DTO 中。
- 列表支持标题/描述关键字、地点、LOST/FOUND、父分类及有效后代、事发时间起止、处理状态组合筛选。页码默认 1、每页默认 10、上限 100；排序仅允许 publishedAt/eventTime 与 asc/desc，追加 ID 同向排序。参数绑定与 LIKE 转义处理 `%`、`_`、`!`。分类树单次读取，不对每条信息查询分类；公开列表一次分页查询加计数查询。
- `GET /api/items` 为授权后台管理列表，可按作者 ID 和状态筛选，返回私有管理 DTO。`POST /api/items/{id}/publish`、`/manage-offline`、`DELETE /api/items/{id}` 为单条操作；原作者单条 `/offline` 保留。`POST /api/items/batch-publish|batch-offline|batch-delete` 使用 `{ "ids": [1,2], "versions": { "1": 0, "2": 1 } }`，要求 1～100 个不重复正整数，版本集合必须与 ID 集合完全对应。成功返回实际处理条数。
- 页面入口：首页 `pages/index.html` → `pages/items/public.html` → `public-detail.html`；授权后台由 `pages/items/module.json` 进入 `manage.html`。本人详情页新增发布和删除入口。查询页保留筛选、分页并显示加载、空结果和失败；管理页支持勾选、批量操作及刷新。用户内容通过 `textContent` 写入 DOM。

## 必要跨模块补充

- 复用孙睿 `CategoryAccessService.publicTree()` 完成祖先有效性和分类子树筛选，没有新建分类服务。
- 复用杨程 `review.service.ItemWorkflowService` 作为状态唯一入口；扩展 `batch` 和 `delete`，沿 ID 升序锁定物品行并比较版本，发布/下架沿用已有规则。下架时关闭 PENDING 申请；存在 APPROVED 申请时阻止下架，存在 PENDING/APPROVED 申请时阻止删除。更新和必要业务日志在同一 Spring 事务内回滚。Article 侧仍只做响应适配，不反向注入工作流。
- 登录拦截白名单加入 `/api/public/items/**`，允许游客访问；后台 API 仍走现有 Session、AOP 权限和 CSRF。原 `BatchIdsRequest` 只包含 ID，扩展请求 DTO 增加版本映射，不更改旧公共类。
- 数据库表结构没有变化；新增 `ArticleMapper` 查询和条件逻辑删除 SQL。已有数据库使用现有 `cms_article`、`cms_category`、`cms_claim_request` 和 `sys_operation_log`。

## 验证与人工核查

- 隔离 H2 中运行受影响的 `ArticleR3Test`、`ArticleDraftHttpTest`、`ReviewWorkflowTest`、`ReviewRollbackTest`，覆盖公开范围和序列化隐私、祖先停用、组合筛选、未授权批量、过期版本、非法状态与整批回滚、批量发布下架删除；共 27 个用例通过。页面内脚本使用 `node --check` 检查通过。
- 最终 `mvn verify` 中 110 个测试有 109 个通过，`ContainerSmokeTest` 因沙箱内 Tomcat 端口返回 `-1` 而报错，未生成通过的最终 WAR 构建结论。沙箱外重跑已中止；未再追加验证。
- AI 辅助实现、修复 SQL 转义与测试夹具，并执行上述验证。人工联调时仍需核查真实 MySQL 与 Tomcat 上的页面交互、实际角色授权及分类并发启停；本轮没有声称这些已验证。
