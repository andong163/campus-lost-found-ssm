# 高鸣远第三轮：集成用例与实际部署验证

本轮基于第二轮总合并 `c3f8562`，开发分支 `feature/gaomingyuan-base`。目的：把前两轮的接口链路变成可复现、可清理的验收流程，在**外部 Apache Tomcat 9** 上部署正式 WAR，并发布真实主接口清单。仅完成高鸣远职责；郭金滔、孙睿、袁运轩、杨程的第三轮业务继续分别开发。

## 交付内容

| 文件 | 作用 |
| --- | --- |
| scripts/install-tomcat9.ps1 | 从 Apache 官方下载本地 Tomcat 9，核对官方 SHA-512；不安装 Windows 服务 |
| scripts/test-gmy-r3.ps1 | 一键构建、准备专用库、部署外部容器、HTTP 验收、停止服务和清理 |
| src/test/java/com/team/cms/tools/GmyR3Fixture.java | 唯一测试库、四角色账号、独立连接配置和所有权保护清理 |
| src/test/java/com/team/cms/tools/GmyR3HttpSuite.java | 对实际部署地址发 Java HttpClient 请求，验证真实会话、权限、分类、草稿和审核 |
| src/test/java/com/team/cms/tools/GmyR3ApiCatalog.java | 从实际 Controller 与 DTO 汇总路径、字段、权限和 CSRF 覆盖情况 |
| scripts/export-api.ps1 | 更新 docs/openapi.json 和兼容导入文件 docs/openapi.yaml |
| docs/api-contracts-current.md、docs/gmy-r3-api.http | 当前协议、原/新审核路由差异及人工请求样例 |
| GmyR3AcceptanceGuardTest、GmyR3ApiCatalogTest | 防止测试指向业务库/远程站点/越界目录，核对已记录接口和引用有效性 |

生产 Java、POM、页面和 SQL 迁移保持第二轮基线，不引入 Spring Boot，也不接管组员第三轮业务。验收类只在 src/test，正式 WAR 不包含这些工具或测试驱动。

## 环境与运行

要求 Windows PowerShell、Maven、JDK 11、本机 MySQL，以及项目现有被忽略的 jdbc-local.properties 或对应环境变量。工具只复用本机 cms_content 的连接凭据来建立**另一个全新库**，不会对 cms_content 执行建表、清空或迁移。MySQL 账号需要创建和删除专用测试库的权限。

```powershell
# 导出当前实际 Controller/DTO（没有新增 npm/Python 运行依赖）
./scripts/export-api.ps1

# 安装到本项目 .tools，核对官方散列；已有外部 Tomcat 9 可直接指定目录
./scripts/install-tomcat9.ps1

# 完整构建 + 外部 Tomcat + 专用 MySQL + 请求验收 + 自动清理
./scripts/test-gmy-r3.ps1

# 已完成构建且源码未变时复跑，仍会创建全新的库和容器实例
./scripts/test-gmy-r3.ps1 -SkipBuild

# 使用其他本机容器位置或空闲端口
./scripts/test-gmy-r3.ps1 -TomcatHome 'D:/tools/apache-tomcat-9.0.123' -Port 8096
```

脚本默认只绑定 `127.0.0.1:8095`。每次创建 `.local/gmy-r3-十二位编号/`，`tomcat/` 是独立 CATALINA_BASE；正式 `target/cms.war` 原样复制到 webapps/cms.war。进程入口是该官方发行版的 `org.apache.catalina.startup.Bootstrap`，类路径仅含 bootstrap.jar 和 tomcat-juli.jar；应用依赖来自 WAR。不是 `LocalServer`、MockMvc 或嵌入式 Tomcat 冒充外部部署。

本次验证的外部发行版为 Tomcat 9.0.123，项目测试用嵌入式版本仍维持原 POM 的 9.0.122。JDK 仍为 11，没有为配合本机 Tomcat 11 把 javax.servlet 工程改成 Jakarta/Boot。

## 每次验收过程

1. 核对外部容器为 9.0.x、端口可用，运行现有 SSM 源码/WAR 检查。
2. 建立 `cms_gmy_r3_十二位编号`。执行新库 init.sql，并复执行 003、004、005，核对增量权限脚本在真实 MySQL 上可重复执行。准备 ADMIN、EDITOR、REVIEWER、VIEWER 四类随机口令账号。
3. 把连接配置与临时账号凭据写入忽略的验收目录；JVM 仅接收配置路径，命令行不放数据库口令。绑定本机地址后启动外部 Tomcat，等待实际应用健康。
4. 用独立 Cookie 会话执行 76 项请求/断言：健康/就绪、匿名边界、注册伪造角色、重复注册、用户/权限访问、分类新增与重名、草稿创建/编辑/版本、私有隔离、缺 CSRF、真实引用拒删、提审/驳回/修改重提/批准、撤回、自审回避、撤权/停用与退出失效、日志权限及实际首页。
5. 每个 API 请求核对 HTTP/code、Result 结构、X-Request-Id 和错误脱敏；令牌要求 no-store。报告只保存检查名、路径、状态、耗时和 requestId，不保存 Cookie、令牌、口令或响应正文。
6. 无论成功或失败，停止本次启动的进程；清理仅在库名、目录后缀、夹具所有者及库内随机标记全部匹配时执行。再次查询确认专用库已不存在，删除连接配置与账号凭据，保留 cleanup.json、verification.json 和本地日志。

如果进程异常中断使自动清理未运行，在项目根目录使用本次验收产生的 classpath 和**原验收目录**恢复清理，不能把目录或库名换成业务库：

```powershell
. ./scripts/common.ps1
$jdk = Resolve-CmsJava $null
$cp = 'target/test-classes;target/classes;' + (Get-Content target/gmy-r3-classpath.txt -Raw).Trim()
& "$jdk/bin/java.exe" -cp $cp com.team.cms.tools.GmyR3Fixture cleanup '.local/gmy-r3-替换为本次十二位编号'
```

先确认本次外部 Tomcat 已停止。正常执行完成后，重复 cleanup 会识别既有清理记录并安全退出。工具不递归删除验收目录，保留失败诊断材料；保留日志不等于保留测试库或凭据。

## 验收结果与边界

2026年10月29日已完成外部 Tomcat 9.0.123 + 真实隔离 MySQL 验收，两次独立运行各 76 项检查通过；两次服务、专用库、账号及配置均已清理。现有回归加新增验收守卫/主接口检查共 111 项 Java/部署测试、16 项前端测试、6 项污染包反例通过。可提交的脱敏环境和结果见 [gmy-r3-verification.json](gmy-r3-verification.json)，每次原始报告在 `.local/gmy-r3-*/verification.json`。

本轮没有覆盖尚未交付的公开检索、批量、认领、联系方式策略或真实交接，也没有把第二轮已有发布端点当成第三轮全组完成。所有写接口 CSRF 的扩展仍属郭金滔第三轮；主接口文件诚实标注现有覆盖边界。前端页面本轮未改动，自动化验收使用真实 HTTP 和首页静态检查，不声称新执行了整套浏览器交互。

执行中先遇到沙箱读取本地 aspectjweaver 依赖受限，允许本机构建后恢复；主接口契约测试曾发现 JSON 内存数字节点与文件解析节点类型不同，统一序列化/解析表示后继续逐字段比较，未放宽接口断言。没有把这些工具问题记作组员业务错误。

## 官方依据

- [Apache Tomcat 9 下载与发行包校验](https://tomcat.apache.org/download-90.cgi)：本次使用 Core zip，并核对官方 SHA-512。
- [Tomcat 9 RUNNING.txt](https://tomcat.apache.org/tomcat-9.0-doc/RUNNING.txt)：使用独立 CATALINA_HOME/CATALINA_BASE 和 JDK 的发行版部署方式。

## 提交前复核

2026年11月4日重新执行 4 项验收守卫/主接口契约测试和 SSM 源码检查，均通过。本次个人第三轮成果仍基于第二轮总合并 c3f8562。远程 develop 已新增郭金滔第三轮提交，待总集成时适配；本次没有将这些成员的新代码合入个人交付，也没有将10月29日外部部署记录写成11月4日新运行。
