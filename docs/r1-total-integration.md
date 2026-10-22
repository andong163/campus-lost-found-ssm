# 五人第一轮总合并与后续接入

日期：2026年10月22日。保持 Java 11、传统 Spring + Spring MVC + MyBatis、MyBatis-Plus 手动整合及 Tomcat 9 WAR，版本仍为 0.2.0-SNAPSHOT。

## 第一轮接收范围

| 成员 | 接收的个人分支提交 | 第一轮交付 |
|---|---|---|
| 高鸣远 | bbcfd4d（含 1c6b142） | 公共组件、错误与版本规则、目录、十表字段、契约和打包检查 |
| 郭金滔 | 7f25b31 | 注册、登录、退出、当前用户、真实身份与权限 AOP、管理员初始化 |
| 孙睿 | a1531b7，已有 PR #1 与补丁 #2 | 分类新增、管理树、公开树、统一可绑定判断与权限接入 |
| 袁运轩 | 16612c5（含 61f55ad），已有 PR #3 | 物品实体、DTO、草稿新增和本人详情、行锁与版本原语、会话令牌 |
| 杨程 | d9fb331 | 结构化审计、授权日志分页、审核记录与认领数据访问契约 |

原始提交和个人 feature 分支保留。使用独立 integration 分支解决冲突并追加记录，再正常合入 develop，不强推、不重写历史。实际 PR 与合并记录在 team-iteration-plan.md 登记；后续 R2～R4 条目保持待执行。

本次实际记录：高鸣远 [PR #4](https://github.com/andong163/campus-lost-found-ssm/pull/4)、袁运轩已有 [PR #3](https://github.com/andong163/campus-lost-found-ssm/pull/3)、杨程 [PR #5](https://github.com/andong163/campus-lost-found-ssm/pull/5)。郭金滔原有合并为 83713e3，孙睿与补丁为 PR #1、#2。实时状态以 PR 为准。

## 冲突处理与固定接口

1. MVC 同时保留登录拦截器与 pages/assets 静态资源映射，未知 API 仍返回统一 JSON 404。
2. 身份入口沿用 CurrentUserContext.require() 与单字符串 @RequirePermission，不另造 CurrentUserProvider 或 RequiresPermission。现有权限种子及 article:create/article:edit 等已用代码保留；41 个规划权限常量不会自动授权。未来迁移从新编号开始，不回写 003。
3. 只保留一个 Article 和一个 ArticleMapper.xml。实体沿用袁运轩的 ArticleStatus、ItemType、ResolutionStatus 枚举，同时保留杨程的 isFound()/isLost()。
4. Mapper 保留本人草稿查询、selectByIdForUpdate(Long)、枚举参数的 updateStatusIfVersion、带发布时间的 updateStatus、带结案时间的 resolve。旧 long 调用可自动装箱；不增加同名 XML statement 或第二个 Mapper。发布时使用带 publishedAt 的方法。
5. 自定义 SQL 的旧状态、version、deleted 条件保留；结案原语保留 OPEN 条件。真正的审核、认领和结案授权仍由后续 Service 实现，Controller 不能直接调用原语跳过规则。
6. CSRF 接口返回 Result.data 字符串；登录后 GET /api/auth/csrf，新增草稿通过 X-CSRF-Token 提交。R1 只覆盖 POST /api/items，不宣称注册、登录、退出或其他模块已统一防护。
7. AuditTemplate 包装事务服务调用，在事务完成后写成功或失败日志。操作日志使用独立事务并隔离日志失败；ReviewRecord 仍应与业务状态同事务。不能在尚未提交的外层事务里提前记录最终成功。

完整签名见 r1-contracts.md 和 contracts/r1-contracts.json（R1-INTEGRATED-2）。后续不得各自恢复两套实体或重复扫描服务接口。Article 的状态比较使用公共枚举，调用 String 参数的 updateStatus 时显式传枚举的 name()。

## 验证

| 阶段 | Java 测试 | 失败／错误／跳过 | 打包反例 |
|---|---:|---|---:|
| GMY R1 接入已有认证分类 | 52 | 均为 0 | 6 项通过 |
| YYX R1 接入公共基线 | 62 | 均为 0 | 6 项通过 |
| YC R1 与第一轮总基线 | 74 | 均为 0 | 6 项通过 |

最终含 73 项单元和集成测试、1 项独立 JVM 的真实 WAR 部署测试；WAR 34 个运行 JAR，无 Boot、私密配置、测试运行时或本地工具。原始报告在本机 target，清理环境信息后的阶段快照见 r1-integration-verification.json。

新增 R1CompatibilityTest 覆盖两个 Mapper 契约兼容、旧版本／已删除记录不写入、日志真实授权及参数校验、业务回滚、审计表失败不撤销已提交业务，以及审核记录与认领查询关闭原语可执行。

测试使用隔离 H2 和临时容器，未写实际 MySQL 业务库，也未执行 001～003。H2 不代替后续 MySQL 并发、索引和部署验收。

## 组员下一步

先提交或备份手头改动，在自己的原 feature 分支执行：

```powershell
git fetch origin
# 保持在自己的 feature 分支：
git merge origin/develop
./scripts/build.ps1
```

没有后续修改时通常可快进；已有 R2 改动时，处理同处修改冲突并重测。不 reset、不强推、不需要重新克隆。同步后继续下一轮，避免基于旧 String 版 Article 或不完整 Mapper 开发。

| 成员 | 下一轮保留任务 |
|---|---|
| 高鸣远 | 公共布局、导航、分页与统一请求组件 |
| 郭金滔 | 用户与角色权限管理、停用撤权影响既有会话 |
| 孙睿 | 分类修改移动排序启停删除、关联限制与页面 |
| 杨程 | 在稳定 Mapper 上实现统一内容工作流与审核工作台 |
| 袁运轩 | YC R2 合并后接入编辑、提审、撤回与相关页面 |

R1 仍没有完整公开检索、发布审核、认领核实、交接结案和业务页面；按原 R2～R4 分工继续。SQL 原语和 DTO 已提交不代表对应业务已完成。
