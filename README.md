# 基于 SSM 的校园失物招领信息管理系统

这是高鸣远负责的基础工程，已按五人分工及新需求从通用 CMS 调整为校园失物招领场景。版本为 0.2.0-SNAPSHOT。当前提供可运行 SSM、公共组件、十表结构与升级工具；注册登录、分类、信息、审核认领、业务日志及页面仍由相应组员接入。

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

## 数据与协作

sql/init.sql 为全新十表安装；sql/migrations 为旧九表的增量升级。保留 cms_article 表名，新增业务字段与 cms_claim_request。EDITOR 显示为学生，四个既有角色代码保持兼容；角色种子没有用户口令，注册低权限与接口授权由郭金滔实现。

本地 jdbc-local.properties 同时被 Git 与 Maven WAR 排除。共享仓库尚需配置远程地址；各成员使用自己的 feature 分支和身份提交，由高鸣远合入 develop。参考 [Git 流程](docs/git-workflow.md)、[需求](docs/requirements.md)、[架构](docs/architecture.md)、[开发记录](docs/development-record.md)。

学校项目以实验二 SSM 为基础升级 CMS，仍需保留独立开发、AI 人工校验与修改痕迹。EMP 实验的单独交付以教师安排为准。

## 接口与官方参考

[OpenAPI](docs/openapi.yaml) 可导入 ApiFox，当前只描述实际实现的健康和就绪接口；业务接口清单在模块接入文档中注明为规划。

- [Spring 5.3 MVC](https://docs.spring.io/spring-framework/docs/5.3.25/reference/html/web.html)
- [Spring 5.3.39 核心与事务](https://docs.spring.io/spring-framework/docs/5.3.39/reference/html/core.html)
- [MyBatis-Plus 配置](https://baomidou.com/en/getting-started/config/)
- [Tomcat 9](https://tomcat.apache.org/download-90.cgi)
