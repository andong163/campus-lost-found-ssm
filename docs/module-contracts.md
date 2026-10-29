# 校园失物招领模块接入约定

第一轮的具体 DTO、版本参数、41 个权限码、模块签名和目录以 [GMY R1 公共契约](r1-contracts.md) 及 [机器清单](contracts/r1-contracts.json) 为准；本页保留整体业务规则。Java 常量不是已实现的鉴权服务，不能替代真实权限检查。

## 全组技术约束

所有模块严格使用 Spring、Spring MVC、MyBatis 的传统 SSM 整合方式，禁止 Spring Boot。不得添加 Boot 父 POM／BOM、`org.springframework.boot` 依赖、任何 Boot starter 或 Boot Maven 插件；不得新增 Boot 启动类、自动配置或可执行 JAR。MyBatis-Plus 按评分要求作为 MyBatis 增强保留，只能使用现有手动配置，不引入 MP Boot starter。

提交合并前检查 pom.xml、源码入口和 WAR 依赖；新增组件通过显式 Bean／配置类接入。交付形态保持 WAR + 外部 Tomcat 9。本地 Tomcat 测试工具留在 src/test，不能移入业务源码或作为发布运行时。

## 容器与映射

根容器管理 Service、Mapper、事务和公共切面；MVC 子容器管理 Controller 与 Advice，并独立启用 AOP。根切面可以按注解或方法匹配 Controller，已有真实 MVC 拦截回归测试。Controller 权限仍由郭金滔实现，配置启用 AOP 不代表所有业务已经鉴权。

Mapper 使用 @Mapper。复杂关联 XML 放 `src/main/resources/mapper/**/*.xml`，namespace 与接口全名一致；MP 专用工厂同时加载 XML 和 BaseMapper。新 @Configuration 需要显式导入，避免父子容器重复扫描。

## API 与 DTO

基础路径 `/cms/api`；成功 HTTP/code=200，统一 code/message/data。400 参数、401 未登录、403 越权、404 不存在或不可公开、409 状态／版本冲突、500 系统错误、503 基础依赖不可用。返回 X-Request-Id，不返回 SQL、堆栈、密码或认证细节。

分页可继承 PageQuery，@Valid @ModelAttribute，默认 page=1、size=10，上限100。排序字段映射白名单，再追加 ID 保持稳定；日期范围和分类子树由模块明确处理。LIKE 使用参数绑定，不把请求字符串拼成 SQL。

批量请求复用 BatchIdsRequest：`{"ids":[1,2]}`。限制1～100个不重复的正整数，空、null、小数、重复和非法值返回400。所有权、当前状态与版本仍在 Service 检查；一项失败整批回滚，不根据 ID 数量假定成功。

## 数据与共享值

可变业务实体继承 BaseEntity；MP 注入自动填充、逻辑删除与乐观锁。自定义 XML、Wrapper 和批量 SQL 自行维护 deleted 过滤、updated_at、version 以及预期状态条件；SQL影响行数0返回409，不能假定插件覆盖所有路径。

ItemType=LOST/FOUND；ResolutionStatus=OPEN/RESOLVED；ClaimStatus=PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED；ArticleStatus 延续六种 CMS 状态。cms_article 新字段参考 sql/init.sql。contact_info 与认领证据只能进入私有 DTO，禁止直接返回 Entity。

cms_claim_request 的 active_applicant_id、approved_article_id 是数据库生成列；Entity 不映射它们，或明确禁止 insert/update。两类唯一索引保护有效申请及有效通过数量，终态记录允许保留多次申请历史。捕获实际 DuplicateKeyException 并转为409安全提示。

## 模块职责与目标 API

以下接口是规划，当前只有 system 接口实现。

| 成员 | 包 | 目标路径与依赖 |
|---|---|---|
| 郭金滔 | auth | /auth、/users、/roles、/permissions；提供 CurrentUser 和权限注解 |
| 孙睿 | category | /categories、/public/categories；提供分类树及可绑定校验 |
| 袁运轩 | article | /items、/items/mine、/public/items；批量、submit、withdraw、edit-draft、offline、contact |
| 杨程 | review、claim、audit | /reviews、/claims、/items/{id}/claims、/items/{id}/resolve、/operation-logs |

注册只赋予低权限 EDITOR。学生允许本人单条下架，公开发布和批量管理由授权管理角色承担。分类新增绑定与启停／删除使用一致事务锁顺序；公开列表与详情都应用祖先有效性过滤。

## 共同状态入口

杨程维护 ItemWorkflowService，袁运轩调用提审、撤回、发布、下架、修改和结案入口。具体服务尚待实现，不能绕过它直接设置状态。

主流程 DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED → OFFLINE；待审可以撤回草稿；驳回修改后重提。已发布先下架再转草稿编辑，修改后重审；OFFLINE 内容未修改且分类有效、未结案时才能恢复发布。审核人不能审核本人内容，记录与状态同一事务。

认领创建、批准、取消和交接均先锁定 cms_article 行，再处理申请。批准前复核 FOUND、PUBLISHED、OPEN 和所有权；分类停用时只允许原申请继续核实。批准不会自动结案；真实交接后 APPROVED → COMPLETED，物品 OPEN → RESOLVED，记录 resolved_at 并关闭其他待审申请。事务方法经公开 Service 代理调用，避免内部自调用失效。

请求诊断过滤器只记录方法、路径、状态、耗时和 requestId。业务审计由杨程保存，排除密码、内容、联系方式与证据；审计失败可定位，不让正常操作重复执行。内容审核记录与认领记录属于业务事务。
