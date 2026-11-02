# 第二轮总合并与同步说明

日期：2026年10月29日。保持 Java 11、传统 Spring MVC + Spring + MyBatis、WAR / Tomcat 9；没有引入 Spring Boot。

## 合入范围

基于 develop `662ce6e`（已含郭金滔 `1edd3be`），依次接入高鸣远 `db41d6b`、孙睿 `508ecc9`、杨程 `047674a`、袁运轩 `192209c`。保留各人的原始提交与合并关系，不重写组员分支。

袁运轩接入时存在审核 Controller、审核 DTO、首页和共享 CSS 四处冲突。本次保留公共首页全部可见文案、布局和样式；保留杨程审核与状态规则，并适配袁运轩本人列表、详情、编辑、提审等页面。

## 统一契约

- `review.service.ItemWorkflowService` 及实现由杨程维护，负责提审、撤回、转草稿、发布、下架等状态命令。`article.service.ItemWorkflowService` 现在仅作为旧页面的响应适配层，所有写操作委托杨程服务，不再直接更新状态或写审核记录。后续轮次继续扩展杨程的服务，不重新实现状态机。
- 审核决定统一进入 `ReviewService.decide`，审核记录与状态更新处于同一事务。旧的 `/api/reviews/{id}/decision` 与兼容 `/approve`、`/reject` 路由使用独立请求 DTO，保留原路径和返回格式；不能自审，驳回需意见，非法状态或旧版本返回 409。两个审核写入口均检查 CSRF。
- 保留杨程 `/api/reviews`、`/history` 和 `/items/{id}/review-records`；保留袁运轩 `/pending`、`/approved`、`/items/{id}/reviews`。新待审队列同样排除本人信息。查看权限不能代替决定权限，角色撤权按郭金滔的新会话重载机制立即生效。
- 新增 `ArticleCategoryReferenceGuard` 查询真实未删除物品引用。物品绑定与分类删除在同一事务中锁同一个分类行，避免校验后删出孤立引用。此为本轮单分类绑定/删除整合；第四轮其他树变更、多实例与认领并发回归仍按原分工完成。
- 首页模块清单接通已交付的分类、我的信息与审核入口；完整身份管理、认领、交接及日志业务页面仍按后续轮次开发。物品/审核页面接入公共外壳及请求组件，退出清空私有内容，晚到响应不能跨会话回填。分类管理保留孙睿的页面布局，复用同源请求与 CSRF 组件。

## 数据库升级与启动

本次只在唯一命名的临时 MySQL 测试库验收，没有升级各成员的真实业务库。

已有数据库：确认 003 已执行后，依次执行 `sql/migrations/004-item-review-permissions.sql`、`sql/migrations/005-r2-category-permissions.sql`。都是增量、幂等的权限元数据脚本，不覆盖已有用户数据。005 只向 ADMIN 默认授予分类管理；学生、审核员没有自动获得分类写权限，其他角色按管理员授权管理。

全新数据库执行更新后的 `sql/init.sql` 即含本轮权限元数据。003、004 已发布脚本不回写。数据库准备好后重新登录，再重新构建/启动服务：

```powershell
./scripts/build.ps1
./scripts/run-dev.ps1 -Port 8088
```

首页 `/cms/pages/index.html`；分类 `/cms/pages/category/index.html`；本人信息 `/cms/pages/items/mine.html`；审核入口 `/cms/pages/review/pending.html`。杨程原工作台 `/cms/review/review.html` 继续可用。

## 验证证据

- 最终 `clean verify`：106 项单元/HTTP 测试及 1 项 WAR 部署测试通过，失败、错误、跳过均为 0。
- 16 项 JavaScript 测试和 6 项污染 WAR 反例通过。源码与正式 WAR 校验通过，34 个运行依赖，无 Boot、私密配置或测试运行时混入正式包。
- 新增 4 项集成边界验证：真实物品引用拒删、绑定/删除共用数据库锁、审核旧/新路由与只读权限隔离、分类权限脚本幂等且不授予学生管理权。保留原有审核写失败回滚、自审回避、撤回及非法状态测试。
- 真实 MySQL 唯一隔离库：初始化及 003→004→005 重复执行成功；22 项 HTTP 操作完成登录、编辑、旧版本拒绝、提审、无意见驳回拒绝、驳回、修改重提、批准、审核记录读取、无 CSRF 拒绝和真实分类引用拒删。
- 浏览器经 Tomcat 9 与该 MySQL 库完成学生登录→菜单→列表→详情→编辑地点→提审→退出清空→审核员登录→审核队列→批准→待发布列表。1280 宽度无横向溢出。使用的账号、信息与联系方式均为隔离夹具，不是产品真实数据。没有把后续第三、第四轮功能声明为已交付。
- 验收工具修复 Windows 无 POSIX 权限接口和 JDBC 默认占位符读取，工具仍只在测试源码中；不进入 WAR。

机器结果见 `r2-integration-verification.json`。本地原始构建日志以 `.local-r2-total-*.log` 保存，HTTP 结果和截图位于工作区外层 `.qa/r2-merge-check-2026-10-29/`。这些临时证据不包含业务库密码。

## 组员继续第三轮

先保存各自未提交修改，再在本人功能分支同步总基线，不重新克隆、不重置历史、不强推：

```bash
git fetch origin
git switch 本人的功能分支
git merge origin/develop
```

遇到冲突先核对本文件的统一服务与接口约定，再处理本模块差异。以后提交仍只推送自己的功能分支，由高鸣远按轮次合并；main 本轮不更新。
