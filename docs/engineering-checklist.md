# 企业开发规范自检

本表按 2026-11-21 的 CMS 项目源码核对。双师课堂笔记应另按真实听课内容填写。

| 自检项 | 实现与证据 |
| --- | --- |
| 传统 SSM 与外部部署 | SpringConfig、SpringMvcConfig、PersistenceConfig、WebAppInitializer；Java 11、WAR、Tomcat 9 |
| 统一依赖 | pom.xml 固定 Spring、MP 和数据库驱动版本 |
| 父子容器职责 | 根容器管理 Service、Mapper、事务与权限切面，MVC 子容器管理 Controller 和异常处理 |
| 三层架构 | Controller 接收 DTO，Service 执行业务，Mapper/XML 执行数据访问 |
| 统一响应 | Result 使用 code/message/data，GlobalExceptionHandler 统一错误状态 |
| 参数校验 | DTO 使用 JSR303；跨字段对应关系在业务入口补充校验 |
| 声明式事务 | 审核、批量和交接操作使用 Service 事务，已有失败回滚测试 |
| 关联数据库 | sql/init.sql 定义十表及外键、索引、唯一约束，001～007 支持旧库升级 |
| 凭据管理 | 本机 jdbc-local.properties 被 Git 忽略并排除出 WAR |
| 登录与权限 | Session 登录、AuthInterceptor 和 PermissionAspect，停用及撤权回归已覆盖 |
| 内容状态流程 | 草稿、提审、审核、发布、下架、认领核实和实际交接分别处理 |
| 分类关联 | 循环检查、父级停用、内容引用、绑定与删除并发测试 |
| 日志 | RequestLogFilter 记录请求，audit 模块记录业务操作，SQL 使用 SLF4J/Logback |
| MP 使用 | BaseMapper、LambdaQueryWrapper、分页、逻辑删除、自动填充及乐观锁 |
| 查询优化 | 分类批量查询验证 20 次降至 1 次；真实 MySQL 性能数据需单独保留 |
| Git 协作 | 五人分支和四轮集成保留原始作者，修复另行提交 |
| 可复现部署 | build、bootstrap-db、upgrade-db、test-gmy-r4 和 package-gmy-r4 脚本 |
| 人工主导与报告 | 每人补齐实际代码审阅、课堂笔记、六部分报告及真实截图 |

保留登录、权限、参数校验、CSRF、事务和状态约束。重复的字段检查优先交给已有 DTO 注解，查询结果使用正常返回值判断，业务规则集中在现有 Service 中维护。
