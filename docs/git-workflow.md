# Git 协作与多轮合并

共享私有仓库为 [andong163/campus-lost-found-ssm](https://github.com/andong163/campus-lost-found-ssm)。develop 是默认集成分支，main 用于验收后的稳定发布。完整执行方案见 [五人分阶段开发与多次合并分工](team-iteration-plan.md)。

## 成员分支

所有功能分支从最新 develop 建立。本方案复用个人分支完成四轮 PR，合并后先保留分支，下一轮同步最新 develop 后继续开发。

- 高鸣远：feature/gaomingyuan-base。
- 郭金滔：feature/guojintao-auth。
- 孙睿：feature/sunrui-category。
- 袁运轩：feature/yuanyunxuan-article。
- 杨程：feature/yangcheng-review-claim。此前若已使用其他名称，先由高鸣远确认映射，不重复建立两套模块。

每个人设置本人的 Git 用户名与已验证邮箱，提交说明使用中文。高鸣远的 GitHub 账号为 andong163；组员使用本人的账号与已验证邮箱，不能照抄负责人的身份。保留真实贡献和人工校验过程，学校要求的辅助工具使用记录另在报告中如实整理。

## 四轮交付

每轮每人完成一个可验收 PR，PR 内按真实功能多次提交；建议 20 个功能或集成 PR 加 1 个发布 PR。计划编号不是实际 GitHub PR 编号，不为凑数做空提交。

第一轮按高、郭、孙、袁、杨合并，提供身份、分类、草稿与审计基础；第二、三轮按高、郭、孙、杨、袁合并，先提供工作流和认领规则再接信息接口；第四轮按郭、孙、杨、袁、高合并，最终执行完整验收。

## 每次提交与合并

同步前保持工作区干净，执行 `git fetch origin`，再在个人分支执行 `git merge origin/develop`。本轮功能和自测完成后推送自己的分支，发起 base=develop、compare=个人分支的 PR。

PR 说明写清计划 ID、功能目的、依赖、接口与 SQL 变化、测试步骤及结果、未完成项。高鸣远检查本轮范围、SSM 分层、权限、状态、SQL、接口和测试后，选择 Create a merge commit 合并。每合一个 PR，下一位提交人同步最新 develop 并重测，再继续合并。

公共配置、依赖、主接口文件和 SQL 总稿由高鸣远维护，成员提交变更提案或独立迁移草案。各人维护本模块页面、接口片段、测试和过程材料。口令、本地配置、target、.tools、.cache 不提交。

历史邮箱调整后，持有旧副本的成员先备份开发内容并协助迁移到新基线；共享历史之后不随意重写。组员推送个人分支，由高鸣远统一合入 develop。全部验收后再发起 develop 到 main 的发布 PR，达到最终验收条件后创建 v1.0.0 标签。
