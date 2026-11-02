# 基于 SSM 的校园失物招领信息管理系统

校园失物招领 SSM 第一轮整合版，版本为 0.2.0-SNAPSHOT。已接入五人的公共基础、注册登录、分类基础、本人草稿和审计基础；审核认领工作流、完整检索、业务页面等继续按第二到第四轮开发。接入与验证见 [第一轮总合并说明](docs/r1-total-integration.md)，请求示例见 [第一轮接口清单](docs/r1-api.http)。

## 高鸣远第一轮交付

GMY R1 已补齐 [公共接入契约](docs/r1-contracts.md)、[机器可读字段与权限码](docs/contracts/r1-contracts.json)、[十表 86 字段清单](docs/database-schema.md)、模块 Mapper 与页面目录，以及可执行的 SSM／WAR 检查。验收结果及组员接入步骤见 [第一轮完成记录](docs/gmy-r1-completion.md)。

第一轮统一基线在 `develop`，实际 PR 见五人分工登记表。组员在各自分支正常合入最新 `origin/develop` 后继续原轮次开发。

第一轮只有静态建设页。高鸣远第二轮已完成公共布局、真实会话入口、统一 fetch 与分页组件，通过 `feature/gaomingyuan-base` 分支交付，尚未合入 develop。首页 `/cms/pages/index.html` 已按校园失物登记卡方向重做；各业务页面继续由对应成员开发。认证与权限使用郭金滔已接入的真实实现。

## 高鸣远第二轮交付

接入步骤、首页设计依据及验证结果见 [第二轮完成说明](docs/gmy-r2-local.md)。组员维护自己模块的页面和 module.json，不需要共同修改首页。只读分页示例位于 `/cms/pages/examples/shared-components.html`；它供开发接入验证，不加入学生导航。

运行 `./scripts/test-shared-ui.ps1` 检查请求与分页组件，再运行原有 `./scripts/build.ps1` 验证 SSM 和实际 WAR。本轮提交仅包含高鸣远第二轮内容，其他成员后续轮次未在本次标记完成。

## 技术栈硬性约束

全组严格使用传统 **Spring + Spring MVC + MyBatis（SSM）**，不得使用 Spring Boot。现有工程通过配置类手动整合框架，按 WAR 部署到 Tomcat 9；使用注解配置不要求改成 XML，也不表示使用 Spring Boot。

- 禁止引入 `org.springframework.boot` 依赖、Boot 父 POM／BOM、`spring-boot-starter-*` 或 `spring-boot-maven-plugin`。
- 禁止使用 `@SpringBootApplication`、`@EnableAutoConfiguration`、`SpringApplication.run`，不得改为 Boot 可执行 JAR。
- Spring IoC／AOP／事务、Spring MVC 的 DispatcherServlet、数据源、SqlSessionFactory 和 Mapper 均由本工程显式配置。
- 按学校评分要求保留 MyBatis-Plus 作为 MyBatis 增强组件，采用非 Boot 的手动接入方式，禁止换成 MP 的 Boot starter。

`scripts/run-dev.ps1` 使用 `src/test` 中的本地 Tomcat 辅助工具部署实际构建的 WAR；它只用于开发和验证。嵌入 Tomcat 为 test 依赖，不打入交付 WAR。正式部署将 `target/cms.war` 放入外部 Tomcat 9 的 webapps 目录。

## 环境

JDK 11、Maven 3.9、Spring/SpringMVC 5.3.39、MyBatis-Plus 3.5.3.1、MySQL 8.0.16+、Tomcat 9.0.122。WAR 保留工程名 cms-ssm、上下文 /cms 与 javax.servlet 技术栈。Tomcat 11 的 Jakarta 命名空间与本工程不同。版本统一由高鸣远维护。

本机便携 JDK 在 .tools，Maven 缓存在 .cache，两者不进入源码包。组员准备自己的 JDK 11，使用脚本参数 `-JavaHome 'C:\你的JDK11目录'` 或 JAVA_HOME。

## 运行步骤

在 cms-ssm 目录打开 PowerShell：

```powershell
# 第一次填写本地配置，保留已经存在的文件
if (-not (Test-Path src/main/resources/jdbc-local.properties)) {
    Copy-Item config/jdbc-local.properties.example src/main/resources/jdbc-local.properties
}
# 编辑私密文件，配置本机 MySQL；无需发送给其他成员

# 数据库尚未初始化：执行这一个命令
./scripts/bootstrap-db.ps1
# 已有旧版九表：改为执行升级命令，不要重复初始化
# ./scripts/upgrade-db.ps1

./scripts/build.ps1
./scripts/run-dev.ps1 -Port 8088
```

本机旧库已经执行升级；其他成员根据自己的库选择初始化或升级。运行前先阅读 [数据库升级说明](docs/database-upgrade.md)。

访问 `/cms/api/system/health` 检查 Web 链路，访问 `/cms/api/system/readiness` 检查 MySQL、四个角色以及招领和认领结构。健康结果的 application 为 campus-lost-found；这些接口不代表业务系统已经完成，也不提供后台管理页面。

`./scripts/build.ps1` 在构建前检查传统 SSM 和模块目录，构建后检查 WAR 依赖坐标、私密文件、测试运行时及本地工具，并输出 `target/gmy-r1-verification.json`。独立运行 `./scripts/verify-foundation.ps1 -SourceOnly` 可检查源码，`./scripts/test-foundation-checks.ps1` 验证六类污染包确实被拒绝。

可用 CMS_DB_URL、CMS_DB_USERNAME、CMS_DB_PASSWORD 配置连接。外置文件与环境变量遵循文件优先；自定义文件用 JVM `-Dcms.config.file=绝对路径`。本地数据库工具仅允许回环地址和 cms_content，且尊重配置的端口，不操作其他库。

## 本版优化

- MVC 子容器启用 AOP，验证根容器权限切面能拦截 Controller；服务层仍在根容器管理事务。
- 加载 `classpath*:mapper/**/*.xml`，支持复杂关联 SQL 与 MP BaseMapper 共存。
- 批量 DTO 校验 1～100 个正整数 ID，拒绝空、重复、null 和小数 ID，避免错误截断。
- 就绪检查连接失败统一返回 503，并验证新表和字段；设置本地连接及读写超时。
- 补充 ItemType、ResolutionStatus、ClaimStatus 枚举，区分公开内容、物品处理和认领进度。
- 新增认领唯一索引，约束同人重复有效申请与单物品多个有效批准；升级后用真实 MySQL 事务探针验证并回滚。

详见 [优化记录](docs/optimization-record.md) 和 [验证记录](docs/test-record.md)。

## 模块职责

| 成员 | 目录 | 主责 |
|---|---|---|
| 高鸣远 | config、common、system、scripts、sql | 基础工程、公共组件、结构统筹、Git 合并和部署 |
| 郭金滔 | auth | 注册登录、用户角色权限、AOP 与登录拦截 |
| 孙睿 | category | 分类树、排序、启停与分类有效性 |
| 袁运轩 | article | 寻物招领信息、公开／本人检索、批量及页面 |
| 杨程 | review、claim、audit | 审核、认领核实、交接结案、日志与状态服务 |

业务目录保留职责占位，不能将枚举或表结构当作已实现业务。Mapper 标记 @Mapper；XML 放 src/main/resources/mapper。DTO 与 Entity 分开，公开响应排除联系方式与认领证据。详情约定见 [模块接入](docs/module-contracts.md)。

四轮开发、每人每轮的任务与验收、20 个功能或集成 PR 及发布安排，见 [五人分阶段开发与多次合并分工](docs/team-iteration-plan.md)。

## 数据与协作

sql/init.sql 为全新十表安装；sql/migrations 为旧九表的增量升级。保留 cms_article 表名，新增业务字段与 cms_claim_request。EDITOR 显示为学生，四个既有角色代码保持兼容；角色种子没有用户口令，注册低权限与接口授权由郭金滔实现。

本地 jdbc-local.properties 同时被 Git 与 Maven WAR 排除。共享私有仓库为 [andong163/campus-lost-found-ssm](https://github.com/andong163/campus-lost-found-ssm)，默认分支为 develop；各成员使用自己的 feature 分支和身份提交，由高鸣远合入 develop。参考 [Git 流程](docs/git-workflow.md)、[需求](docs/requirements.md)、[架构](docs/architecture.md)、[开发记录](docs/development-record.md)。

学校项目以实验二 SSM 为基础升级 CMS，仍需保留独立开发、AI 人工校验与修改痕迹。EMP 实验的单独交付以教师安排为准。

## 接口与官方参考

[OpenAPI](docs/openapi.yaml) 可导入 ApiFox，当前只描述实际实现的健康和就绪接口；业务接口清单在模块接入文档中注明为规划。

- [Spring 5.3 MVC](https://docs.spring.io/spring-framework/docs/5.3.25/reference/html/web.html)
- [Spring 5.3.39 核心与事务](https://docs.spring.io/spring-framework/docs/5.3.39/reference/html/core.html)
- [MyBatis-Plus 配置](https://baomidou.com/en/getting-started/config/)
- [Tomcat 9](https://tomcat.apache.org/download-90.cgi)
