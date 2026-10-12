# 校园失物招领基础工程验证记录

日期：2026年10月12日，版本0.2.0-SNAPSHOT。以下由Codex实际执行，高鸣远需自行复现、审查并补充本人截图。原0.1.0基础版为13项，本版新增回归后为22项。

## 完整构建

JDK11、Maven、Tomcat9，执行 scripts/build.ps1，最终 BUILD SUCCESS。

| 测试类 | 数量 | 结果 |
|---|---:|---|
| ContainerSmokeTest | 1 | 通过 |
| FrameworkIntegrationTest | 6 | 通过 |
| LocalDatabaseSupportTest | 3 | 通过 |
| RequestLogFilterTest | 1 | 通过 |
| WebContractTest | 10 | 通过 |
| WarDeploymentIT | 1 | 通过 |

共22项，失败0、错误0、跳过0。框架与Web测试使用隔离H2；包括父子容器、MP CRUD／填充／分页／逻辑删除／乐观锁、事务回滚、XML映射、Controller AOP拦截、批量参数、小数ID拒绝、MDC恢复以及统一错误结果。

## 独立WAR与启动优化

打包后由独立JVM部署生产WAR，父类路径只有工具类、Tomcat与annotations API，确认真实Servlet初始化及HTTP health=200、未知接口=404。没有把H2、测试Controller或测试Mapper放入WAR。

首次WAR检查在90秒上限内未完成，日志停留在容器初始化。限制本地JAR扫描后首次通过的启动检查为81359毫秒；改用Tomcat解包WAR并消除空XML配置提示后，本机本次同一检查为7236毫秒，最终完整构建约21秒。该值仅表示此机器和此基础工程的运行记录，不代表业务查询性能或其他环境的保证。

本地启动器只扫描spring-web的SCI，不使用JSP/TLD；以后引入其他SCI时通过 cms.scan.extra 追加逗号分隔JAR匹配式，增加JSP时需调整TLD策略。普通外置Tomcat部署不受该本地启动器策略影响。

## 真实MySQL升级

本机MySQL8.0.44，目标cms_content。原九表内容表为空，增量升级新增六个招领字段及cms_claim_request，保留四个角色代码，更新默认显示名称。没有修改其他数据库或覆盖旧文章。

真实数据库探针验证同人同物品的重复有效申请被拒绝、同物品第二个APPROVED被唯一索引拒绝，并验证完成状态与结案时间更新；全部探针数据回滚。再次执行升级跳过ALTER并重复验证成功。这里验证的是SQL约束，认领Service的权限、行锁和真实并发仍由杨程实现后测试。

## 实际HTTP与打包检查

临时在127.0.0.1:8089启动生产WAR，GET health=200、GET readiness=200、未知路径=404、POST health=405，HTTP/code一致且有X-Request-Id。readiness实际连接MySQL，并检查itemSchema及claimSchema；记录见http-verification.json。测试进程已关闭。

检查WAR中私密配置和测试文件为0，公开源码匹配本机私密口令为0。原始Maven报告及本地日志位于target或忽略文件，不进入源码归档。

## 仍由组员接入的范围

登录权限实现、分类规则、信息CRUD、审核状态服务、认领核实交接、业务审计、页面及N+1 SQL优化仍需对应成员开发。22项是基础工程回归数，不能当作完整失物招领系统验收结果。
