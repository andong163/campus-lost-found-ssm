# 基础工程验证记录

日期：2026年10月12日。以下由 Codex 实际执行，人工复现和截图请由高鸣远补充。

## 构建与自动化

Microsoft OpenJDK 11.0.32.1、Maven、Tomcat 9.0.122；执行 `scripts/build.ps1`，最终 BUILD SUCCESS。

| 测试类 | 数量 | 结果 |
|---|---:|---|
| FrameworkIntegrationTest | 5 | 全部通过 |
| WebContractTest | 6 | 全部通过 |
| ContainerSmokeTest | 1 | 通过 |
| WarDeploymentIT | 1 | 通过 |

共13项，失败0、错误0、跳过0。覆盖父子容器、MP CRUD和填充、分页、逻辑删除、乐观锁冲突、声明式事务的检查异常回滚、参数验证、404/405/409/500/503统一结果及日志标识。

前三类数据库测试使用隔离 H2 MySQL 模式，不冒充真实 MySQL。最后一类在打包后启动独立 JVM，只让 Tomcat 与 annotations API 出现在父类路径，WAR 使用生产类和自己的依赖；通过健康接口200和不存在接口404，避免测试环境类路径掩盖部署错误。

原始报告由 Maven 生成在 target/surefire-reports 与 target/failsafe-reports；WAR部署日志在 target/war-smoke.log。它们是构建产物，不进入 Git。

## 真实MySQL

本机 MySQL 8.0.44，专用库 cms_content。显式执行 bootstrap-db.ps1：目标原先没有表，sql/init.sql 成功建立九表和四个角色；插入事务探针后回滚，复查为0。没有创建业务用户、写入真实内容或修改其他数据库。

私密连接文件不输出、不进入Git；打包检查确认 WAR 没有该文件及测试类。后续实际 HTTP 检查结果保存在 http-verification.json，数据库就绪检查仅验证连接和四个基础角色，不证明所有业务已实现。

最终 WAR 在本机 127.0.0.1:8088 运行的检查结果：GET health=200、GET readiness=200、未知接口=404、POST health=405；HTTP 状态与响应 code 一致，均包含 X-Request-Id。运行日志没有 ERROR、Exception 或 ClassNotFound 匹配项。此 readiness 检查实际连接上面的 MySQL 库。

## 尚待全组验证

登录与权限、分类规则、文章业务、审核状态、批量业务、操作审计、N+1优化和完整演示均需对应作者实现后补充用例。当前13项是基础工程的测试数，不是完整 CMS 的验收结果。
