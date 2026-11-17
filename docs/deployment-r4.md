# 第四轮候选版安装升级与外部部署

本文面向组员和验收人员。当前为本地候选基线，非全组最终发布版；实际已测结果见 [GMY R4](gmy-r4.md)。正式合入组员第四轮后，应在同一流程重新验收再发版。

## 环境与配置

使用 JDK 11、Maven、PowerShell 7、MySQL 8.0.16 以上和 Tomcat 9。工程使用 javax.servlet，不能直接放进 Tomcat 10/11。本次实际外部验收容器为 9.0.123；测试依赖中的嵌入 Tomcat 不进入 WAR，也不是正式部署方案。

解压源码包，进入有 pom.xml 的项目根目录。创建自己的本机配置，已有配置不要覆盖：

```powershell
if (-not (Test-Path src/main/resources/jdbc-local.properties)) {
    Copy-Item config/jdbc-local.properties.example src/main/resources/jdbc-local.properties
}
```

本机文件填写回环 MySQL 的 cms_content 连接，账号密码只保存在该文件或环境变量，不进入 Git、WAR、源码包和报告。可用 `-JavaHome 'C:\本机JDK11目录'` 指定 JDK；各机器自行安装 Maven 和 MySQL。

## 新库安装与旧库升级

数据库尚未初始化时执行 `./scripts/bootstrap-db.ps1`。该命令拒绝覆盖已有表，执行 sql/init.sql 后检查十表、角色和认领约束。

已经有本项目数据库时执行 `./scripts/upgrade-db.ps1`，不要重跑 init.sql。升级包含 001/002 结构及 003→004→005→006 权限增量；保留旧用户、分类和自定义角色授权。旧九表的内容表必须为空才允许自动增加物品必填字段，已有文章需要人工确定类型、时间、地点和联系方式，不自动填造这些信息。发现部分升级状态时先核查，工具不会继续拼凑结构。

升级前保留本机数据库备份。MySQL DDL 分阶段提交，不能承诺一次事务回滚整个结构迁移。本次专用 MySQL 验证了空九表升级、部分结构拒绝、非空旧内容拒绝、重复执行、既有数据和授权保留，未对真实业务库执行升级。

## 构建与正式容器

```powershell
./scripts/build.ps1
./scripts/test-shared-ui.ps1
```

构建得到 target/cms.war，并检查无 Boot、口令配置、测试类、便携 JDK 或嵌入式运行依赖。把该文件作为 cms.war 部署到外部 Tomcat 9 的 webapps，确保上下文为 /cms。

在独立目录准备本机配置文件，通过 Tomcat JVM 参数 `-Dcms.config.file=配置文件绝对路径` 指定；或者向 Tomcat 进程提供 CMS_DB_URL、CMS_DB_USERNAME、CMS_DB_PASSWORD。不要把口令写进可共享的 setenv 脚本。变更配置后重启对应容器。

访问 `/cms/api/system/health` 检查 Web；访问 `/cms/api/system/readiness` 检查数据库。后者应为 HTTP 200 及 Result.code=200；数据库不可用或缺少必要结构时为 503。首页为 `/cms/pages/index.html`。

管理员首次初始化按 [身份模块说明](gjt-r3-pages.md) 和 auth 模块的 AdminInitializer 配置执行，使用本机输入的口令，切勿传播初始口令。本项目不在 SQL 或交付文档中预置真实管理员账号口令。

## 第四轮工程验收与候选打包

```powershell
./scripts/test-gmy-r4.ps1 -Port 8098
# 替换为上条命令实际打印的本次目录
./scripts/package-gmy-r4.ps1 -VerificationPath '.local/gmy-r3-本次十二位编号/r4-verification.json'
```

验收使用两个带归属标记的临时库，不写真实业务库。正常或异常结束都会停止专用容器并尝试清理；如记录 cleanupFailed，先根据该验收目录和库内归属标记排查，不运行泛化删库命令。保留不含口令的验收 JSON 与日志用于排错。

打包先验证源码指纹和 WAR 哈希仍与验收时一致，输出新的 dist/gmy-r4-candidate-时间目录，不覆盖旧包。包含源码 ZIP、WAR、验收 JSON、交付哈希清单和说明。源码含可读 Git 历史及实际未提交状态，不含 .git 内部目录或本机依赖。候选包不能当成其他成员第四轮已验收或正式版本标签。

## 常见现象

| 现象 | 排查方向 |
| --- | --- |
| readiness 503 | MySQL 进程、端口、本机账号、表结构及迁移是否完整 |
| 新权限接口返回 403 | 检查 006 种子、当前角色、资源所有权、同会话 CSRF；不能临时放开鉴权 |
| 类加载或 javax/jakarta 不兼容 | 检查 JDK 11 和外部 Tomcat 9，不改成 Boot 规避 |
| 静态页面显示旧内容 | 检查部署的 WAR 哈希、上下文 /cms 及缓存，确认已重载当前包 |
| 验收后无法打包 | 源码或 WAR 已变化，重新验收；不能手改验收 JSON 把失败改成通过 |

更新前保留原 WAR、配置和数据库备份。需要回退时停止本次部署，恢复相互匹配的旧 WAR 与数据库备份；不能仅回退代码就假定新旧数据库字段一定兼容。
