# 孙睿第一轮分类模块集成验收

日期：2026年10月21日。范围为 SR R1，接收孙睿原始提交 `a1531b7`，在已包含郭金滔认证模块的 `develop` 基线上验收。高鸣远个人分支的 GMY R1 保持独立，不随本次合并进入 develop。

实际合并记录：[Pull Request #1](https://github.com/andong163/campus-lost-found-ssm/pull/1)，提交与实时合并状态均可在此核对。

第一轮后续补丁：IDEA 分类服务注入候选提示的排查、修复及同步方式见 [分类注入说明](category-injection-troubleshooting.md)，不包含第二至第四轮功能。

## 本轮交付

- 分类 Entity、Mapper、新增接口和管理分类树。
- 游客可访问的有效分类树；自身或祖先停用时隐藏整棵子树。
- `CategoryAccessService` 的 `isBindable(Long)`、`requireBindable(Long)`、`publicTree()`，供袁运轩第一轮保存草稿时接入。
- 父级存在性、逻辑删除、启用状态、同级重名和请求参数校验；孙睿原有单实例同名并发测试保留。

分类管理接口接入 develop 中已有的 `auth.security.RequirePermission`：新增要求 `category:add`，管理树要求 `category:view`。拦截器只额外放行精确路径 `/api/public/categories`，没有扩大管理接口的匿名访问范围。当前管理员可通过已有 ADMIN 角色访问；本轮不新增生产权限种子、不向学生自动授权。

现有认证模块通过真实登录 Session 和 `CurrentUserContext` 提供身份；本轮沿用实际实现，不新造另一套登录或权限组件。

## 验证结果

首次直接合并时，分类测试因未携带登录会话产生 10 项失败，另有 1 项权限测试被原代码跳过。此次补齐管理接口权限注解、公开树白名单，并将分类测试接入真实注册、登录和数据库权限装载，取消跳过。

执行 `./scripts/build.ps1`：43 项单元与集成测试，加 1 项独立 JVM 的真实 WAR 部署测试，共 44 项全部通过，失败、错误、跳过均为 0。分类测试包含 13 项，验证匿名管理访问 401、普通学生管理访问 403、管理员可操作、非管理员的查看和新增权限相互独立，以及公开树匿名可读且不暴露管理字段。

原始报告保存在本机 `target/surefire-reports`、`target/failsafe-reports`，可共享的数量与打包快照见 `sr-r1-verification.json`。测试使用独立 H2 内存数据库及临时容器；未向实际 MySQL 业务库写数据，也未执行迁移。H2 测试不替代后续 MySQL 并发和性能验收。

## 后续轮次边界

| 轮次 | 保留的开发内容 |
|---|---|
| SR R2 | 修改、移动、排序、启停、逻辑删除、关联限制和分类管理页 |
| SR R3 | 分类选择组件、子树检索及关联查询优化 |
| SR R4 | 分类启停删除与内容绑定的并发边界、多实例策略、索引和性能证据 |

本轮只保留 R1 的树排序输出和祖先状态判断，没有实现第二轮的排序或启停修改接口。权限会话刷新、CSRF 等认证模块的后续任务仍由郭金滔按计划推进。本次测试通过不代表第二至第四轮已完成。

## 合并后接入

集成使用独立分支 `integration/sunrui-r1`，保留孙睿原始提交；不强推或重写 `feature/sunrui-category`，不修改 `feature/gaomingyuan-base` 和 `main`。合并状态及审查记录以对应 GitHub Pull Request 为准。

孙睿及已有个人分支的袁运轩、杨程，在提交或备份自己的修改后，将 `origin/develop` 正常合入个人分支：

```powershell
git fetch origin
# 先切换到自己的个人分支，再执行：
git merge origin/develop
./scripts/build.ps1
```

袁运轩尚未建立个人分支时，可在干净工作区执行 `git switch -c feature/yuanyunxuan-article origin/develop`。无需复制分类文件或重复编写分类有效性判断；新增草稿时调用 `requireBindable(categoryId)`。杨程后续发布、认领功能也复用同一接口。

合并 develop 不会自动改变个人分支。他们同步时需要解决可能的同处修改冲突并重新测试。后续各轮继续在原个人分支新增提交，正常同步后再发起下一轮 PR；不重置分支、不重新克隆，也不将不同轮次挤入同一个完成记录。
