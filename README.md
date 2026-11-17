# 基于 SSM 的校园失物招领信息管理系统

学生登记 LOST/FOUND，授权人员审核发布，学生申请认领，经核实与实际交接后结案。工程严格采用传统 Spring + Spring MVC + MyBatis，MyBatis-Plus 手动接入，JDK 11，交付 WAR 到外部 Tomcat 9，禁止 Spring Boot。

高鸣远第四轮提交分支为 `feature/gaomingyuan-r4`，承接 `integration/r3-compat-20261105`：五人第三轮代码及兼容修复作为前置基线单独保留；远程 develop 仍是独立基线，不能把本地状态写成已经远程合并。高鸣远第四轮工程验收与交付工具已完成；其他成员第四轮尚未进入本次基线，全组最终发布待验收。

## 当前交付

- [高鸣远第四轮说明](docs/gmy-r4.md) 和 [工程报告 PDF](docs/gmy-r4-report.pdf)
- [验收 JSON](docs/gmy-r4-verification.json)、[部署说明](docs/deployment-r4.md)、[评分点对照](docs/school-rubric-matrix.md)
- [架构 E R 业务流程图](docs/diagrams/README.md)，包含可编辑源稿、SVG 和 PNG
- [第三轮兼容验收](docs/r3-total-integration.md)、[需求](docs/requirements.md)、[五人分工](docs/team-iteration-plan.md)
- [当前接口约定](docs/api-contracts-current.md) 与可导入 ApiFox 的 [OpenAPI](docs/openapi.json)，当前 63 个操作

2026-11-08 提交前复验通过 145 项 Java 测试、1 项 WAR 部署、24 项前端检查、6 项架构防护反向检查，以及真实 MySQL + 外部 Tomcat 的 76 项核心和 96 项第四轮接口检查。最新提交前验收结果见 docs/gmy-r4.md 和结构化验收记录。候选包不等于正式发布。

## 准备与运行

准备 JDK 11、Maven、PowerShell 7、MySQL 8.0.16+ 和外部 Tomcat 9。Spring/SpringMVC 5.3.39、MyBatis-Plus 3.5.3.1 及依赖版本由 pom.xml 固定，使用 javax.servlet；Tomcat 10/11 不能直接替代。

```powershell
if (-not (Test-Path src/main/resources/jdbc-local.properties)) {
    Copy-Item config/jdbc-local.properties.example src/main/resources/jdbc-local.properties
}
# 在本机填写数据库配置，不提交或向组员发送口令

# 新库执行这一条；已有数据库使用下方升级命令
./scripts/bootstrap-db.ps1
# ./scripts/upgrade-db.ps1

./scripts/build.ps1
./scripts/run-dev.ps1 -Port 8088
```

已有库按增量升级，不能用 init.sql 重置。旧 CMS 有非空文章或部分结构升级时需要人工核对，工具拒绝自动填造物品字段。本轮验收未修改真实业务库。

开发辅助容器位于 test 源集，不进入 WAR。正式部署把 target/cms.war 交给外部 Tomcat 9，按 [部署说明](docs/deployment-r4.md) 注入本机配置，访问 `/cms/pages/index.html`、`/cms/api/system/health` 与 `/cms/api/system/readiness`。

## 验收与打包

```powershell
./scripts/test-gmy-r4.ps1 -Port 8098
# 将路径替换为验收命令实际输出的本次目录
./scripts/package-gmy-r4.ps1 -VerificationPath '.local/gmy-r3-本次十二位编号/r4-verification.json'
```

验收创建带随机归属标记的专用库，校验真实交接事务并自动清理。打包校验当前源码指纹与 WAR 哈希；输出 dist 下的候选源码 ZIP、WAR、验收与哈希清单。源码包纳入实际未提交内容及可读 Git 记录，排除本机口令、缓存、运行日志、便携 JDK、测试库凭据和 .git 内部目录。修改业务源码后须重新验收。

## 模块归属

| 人员 | 模块 |
| --- | --- |
| 高鸣远 | config/common/system、公共组件、SQL 总稿、集成验收和部署交付 |
| 郭金滔 | auth、身份会话、用户角色权限及 AOP |
| 孙睿 | category、多级分类及查询优化 |
| 袁运轩 | article、物品信息、公开检索、批量与信息页面 |
| 杨程 | review/claim/audit、审核认领、交接状态与审计 |

成员只推送自己的 feature 分支，按既定轮次由高鸣远集成 develop。高鸣远第四轮新增的是验收和交付工程，已有业务仍归原负责人。禁止硬编码真实身份、绕过权限或把审核/核实直接等同于实际归还。代码与私密配置各自维护。

课程项目评分采用 CMS 升级的 20 分口径；实验二 EMP 单独实验及个人 Word/PDF 报告按教师安排完成，不以校园项目自动代替。学校材料与人工审阅填写要求见评分对照文档。
