# 基于 SSM 的企业资讯与知识内容管理系统

这是高鸣远负责的第一阶段基础工程。已建立手工 SSM 整合、公共组件、数据库初稿与测试；用户权限、分类、文章、审核及操作审计由对应组员继续开发。

## 环境与版本

Java 11、Maven 3.9、Spring/SpringMVC 5.3.39、MyBatis-Plus 3.5.3.1、MySQL 8.0.16+、Tomcat 9.0.122。以课程要求的 `javax.servlet` 技术栈构建 WAR；本机 Tomcat 11 使用 Jakarta 命名空间，不能直接部署这一版本。

便携 JDK 在本机 `.tools` 中，已用官方 SHA-256 校验；该目录和 Maven 缓存不会进入 Git。组员克隆后需自行准备 JDK 11，可通过脚本的 `-JavaHome 'C:\你的JDK11目录'` 参数指定。版本属于课程基础工程的固定配置，不表示该技术栈适用于外网生产部署。

## 快速运行

在 `cms-ssm` 目录打开 PowerShell：

```powershell
# 1. 首次配置；若已有私密文件，不要覆盖它
if (-not (Test-Path src/main/resources/jdbc-local.properties)) {
    Copy-Item config/jdbc-local.properties.example src/main/resources/jdbc-local.properties
}
# 编辑上面的私密文件，填写本机 MySQL 用户与密码

# 2. 首次创建项目数据库（本机已初始化时跳过，不覆盖已存在的表）
./scripts/bootstrap-db.ps1

# 3. 运行测试并构建 target/cms.war
./scripts/build.ps1

# 4. 本地运行真正的 WAR；前台终端 Ctrl+C 停止
./scripts/run-dev.ps1 -Port 8080
```

检查 `http://127.0.0.1:8080/cms/api/system/health` 和 `/cms/api/system/readiness`。健康检查验证 Web 链路；就绪检查验证数据库访问和四个基础角色，不代表所有业务模块完成。

也可用环境变量 `CMS_DB_URL`、`CMS_DB_USERNAME`、`CMS_DB_PASSWORD`。私密文件未配置时才使用这些默认映射；部署环境中不要放本地配置文件。若需要指定外置配置路径，给 Tomcat JVM 传入 `-Dcms.config.file=绝对路径`。

## 数据库与凭据

初始化文件为 `sql/init.sql`，目标库 `cms_content`，包含九张关联表和四个角色。脚本没有 DROP/TRUNCATE，也没有用户账号或默认密码。初始化助手发现目标库已有任何表即停止；后续修改使用独立迁移脚本，不能拿初始化文件当作自动升级工具。

`src/main/resources/jdbc-local.properties` 被 Git 忽略，同时被 Maven resources 排除，不进入 WAR。服务器账号凭据从环境或外置配置读取。不要发送、截图、提交这个私密文件。实际业务建议使用专用数据库账号；本机首次初始化使用用户提供的本地账号。

## 项目目录与分工

```text
src/main/java/com/team/cms/
  config/             高鸣远：Spring、MVC、MP、数据源、事务
  common/             高鸣远：统一结果、校验、异常、分页、基础实体
  system/             高鸣远：基础健康与就绪检查
  auth/               郭金滔：用户、角色、权限与登录
  category/           孙睿：多级分类
  article/            袁运轩：文章、检索、发布和批量操作
  review/             杨程：审核及审核记录
  audit/              杨程：业务操作审计
src/test/             基础集成测试与本地工具，不进入 WAR
sql/                  数据库初稿，变更由高鸣远审核
docs/                 架构、接口约定、协作、过程与测试记录
```

业务模块目录目前只保留职责说明。组员按自己的模块开发 Controller/Service/Mapper/Entity/DTO，并以 `@Mapper` 标记 Mapper。新配置类需要显式导入；父容器不扫描 MVC 或其他配置类，避免重复创建组件。

## 测试与下一步

`build.ps1` 验证父子容器职责、MP CRUD、自动填充、分页、逻辑删除、乐观锁、事务回滚、参数与错误返回，以及 Tomcat 9 的真实 HTTP 请求。数据库自动化测试用独立 H2；真实 MySQL 初始化由显式 bootstrap 命令验证，两者证据分开记录。

下一步：高鸣远逐行检查基础配置与公共组件，组员确认表结构、状态和接口，再接入业务模块。尚未建立远程仓库，推送需先提供仓库地址。参考 [需求分析](docs/requirements.md)、[模块约定](docs/module-contracts.md)、[架构设计](docs/architecture.md)、[Git协作](docs/git-workflow.md)、[开发记录](docs/development-record.md)。[OpenAPI](docs/openapi.yaml) 可导入 ApiFox；[测试记录](docs/test-record.md) 说明实际验证范围。

## 官方依据

- [Spring 5.3 MVC 与容器初始化](https://docs.spring.io/spring-framework/docs/5.3.25/reference/html/web.html)
- [Spring 5.3.39 核心与事务](https://docs.spring.io/spring-framework/docs/5.3.39/reference/html/core.html)
- [MyBatis-Plus 手工 Spring 配置](https://baomidou.com/en/getting-started/config/)
- [Tomcat 9 下载与校验](https://tomcat.apache.org/download-90.cgi)
- [微软 JDK 11 便携包地址与校验](https://learn.microsoft.com/en-us/java/openjdk/download-major-urls)
