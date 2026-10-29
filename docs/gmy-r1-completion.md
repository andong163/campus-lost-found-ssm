# 高鸣远第一轮完成记录

计划 GMY R1　2026年10月19日　基础版本 0.2.0-SNAPSHOT

本轮完成公共基线和接入契约，业务模块仍由郭金滔、孙睿、袁运轩和杨程继续实现。代码与验证材料供各成员在最新 develop 基线上接入；本轮不把接口规划、权限常量或建设页当作完整业务。

## 当前提交范围

2026年10月22日总合并补记：下文为最初个人分支交付时的历史范围记录。公共基线已通过 PR #4 合入 develop；五人接入方式见 r1-total-integration.md，原有组员提交与后续轮次保留。原始29项快照保持不变，本次阶段测试见 r1-integration-verification.json。

本轮仅提交到 `feature/gaomingyuan-base`，暂不合入 `develop` 或 `main`，不修改任何组员分支，不重写提交历史，也不执行数据库迁移。组员目前可以继续在自己的分支开发，无需因这次个人分支提交重新克隆或同步。

后续安排 GMY R1 合并时，再由各成员正常同步 `origin/develop`。若个人分支也修改过公共配置或接口，需要届时逐项处理冲突；本次提交不能保证未来合并完全没有冲突。

## 已完成交付

| 任务 | 交付位置 | 实际结果 |
|---|---|---|
| 复现原始基线 | 原有构建与测试 | 22 项测试通过，旧基线没有新增 Boot 或版本迁移 |
| 固定 DTO 和版本规则 | common/api/VersionRequest、r1-contracts.md | 缺失、负数、小数及溢出版本拒绝；旧版本由业务条件更新转 409 |
| 固定角色和权限码 | RoleCode、PermissionCodes、contracts/r1-contracts.json | 4 角色、41 个权限码及维护人；没有默认放行或假 Session |
| 统一错误边界 | GlobalExceptionHandler | 数据约束和并发冲突 409，数据库资源或事务连接建立失败 503，无 SQL 泄漏 |
| 明确分类和状态接口 | r1-contracts.md | 沿用分类分支 isBindable、requireBindable、publicTree；固定工作流方向与命令 |
| 模块接入目录 | mapper 下六个模块、pages 下六个模块、assets | 新模块资源可直接接入；WAR 排除占位和开发说明文件 |
| 静态资源验证 | /cms/pages/index.html、/cms/assets/css/base.css | 两个实际资源返回 200，未知 API 仍为统一 JSON 404 |
| 十表与迁移编号 | database-schema.md、sql/migrations/README.md | 10 表 86 字段，约束索引与维护人已列；003 仅预留，未执行新 DDL |
| 可执行框架与打包检查 | scripts/verify-foundation.ps1、build.ps1 | 拒绝 Boot、私密文件、测试运行时和本地工具，包含重命名 JAR 的坐标检查 |

完整机器结果见 [验证快照](gmy-r1-verification.json)。target 下保留本机原始报告，快照只整理测试数量和构建信息，不复制完整环境属性或口令。

## 实际验证

执行 `./scripts/build.ps1`，JDK 11 下 Maven clean verify 成功。29 项 Java 测试全部通过，失败、错误、跳过均为 0。

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| ContainerSmokeTest | 1 | 真实 Servlet 初始化和 MVC 容器 |
| FrameworkIntegrationTest | 6 | 事务、Mapper XML、MP 等原有基线 |
| LocalDatabaseSupportTest | 3 | 本地配置与数据库工具限制 |
| RequestLogFilterTest | 1 | 请求诊断上下文恢复 |
| WebContractTest | 13 | 校验、版本、枚举、409／503、安全错误和静态资源 |
| FoundationContractTest | 4 | 权限与角色、公共状态、十表字段、传统 SSM 架构 |
| WarDeploymentIT | 1 | 独立 JVM 加载真实 WAR，检查 health、404、页面和 CSS |

构建后检查 WAR 的 34 个运行 JAR，Spring Boot、私密配置、测试运行时和本地工具均未发现。嵌入 Tomcat 只用于 test，不进入实际交付 WAR。

另执行 `./scripts/test-foundation-checks.ps1`，六个反例全部按预期拒绝：私密 JDBC 配置、.env.local、测试工具类、Boot JAR、嵌入 Tomcat 运行 JAR、改名后仍带 Boot Maven 坐标的 JAR。反例使用临时副本，真实 WAR 不受修改，临时文件在验证后清理。

本轮测试不表示业务权限、实际认领并发或全站流程已通过；这些随相应成员的 PR 增加验证。本轮没有改写现有 SQL 或写入业务数据库，也没有合并孙睿的分类实现。

## 组员接入顺序

1. GMY R1 合入 develop 后，各人先提交或备份自己的工作，再将最新 origin/develop 合入个人分支。持有旧历史的副本按迁移说明先处理，不强行混合无关历史。
2. 郭金滔按契约实现 CurrentUserProvider、RequiresPermission、Session／CSRF、注册登录和本机管理员初始化，补上真实鉴权，发布 GJT R1。
3. 孙睿沿现有 CategoryAccessService 签名接入郭金滔权限组件，补齐分类管理接口保护后再验收 SR R1；其当前远程分支的 TODO 不代表已鉴权。
4. 袁运轩复用 VersionRequest 和公共枚举，提供草稿与稳定 ArticleMapper；杨程随后接入审计和状态契约，不能双向注入 ArticleService。
5. 接口、权限码和数据库字段变化先提交提案，高鸣远审核并更新公共版本，避免各成员自行修改全套配置或迁移编号。

## 复现命令

```powershell
./scripts/verify-foundation.ps1 -SourceOnly
./scripts/build.ps1
./scripts/test-foundation-checks.ps1
```

运行环境由 README 说明；有需要时以 -JavaHome 指向 JDK 11。Maven、Java 和测试报告的实际版本见验证快照。运行口令保存在已忽略的本地文件或环境，不进入 Git 和 WAR。
