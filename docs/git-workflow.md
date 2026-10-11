# Git协作与合并

当前只创建本地仓库。main 是稳定分支，develop 是集成分支，高鸣远在 feature/gaomingyuan-base 工作。其他四人从确认的 develop 基线创建自己的分支：

- feature/guojintao-auth
- feature/sunrui-category
- feature/yuanyunxuan-article
- feature/yangcheng-review

每个人配置本人姓名与邮箱，按功能小步提交。同步前保持工作区干净，`git fetch origin` 后在个人分支 `git merge origin/develop`。完成功能与自测后发起合并请求，由高鸣远检查分层、权限、SQL、接口与测试再合并。

公共配置和 SQL 总稿由高鸣远维护；成员增加表字段先提交独立变更脚本。合并保留原作者历史，不能用负责人身份替代成员贡献。Codex 创建的基础工程提交使用明确的 AI 辅助作者标识，不冒充高鸣远手写代码；人工校验与修改另行提交。

本地配置、target、.tools、.cache 已忽略。合并请求须写清功能目的、接口与 SQL 变化、测试证据、依赖模块和未完成项。通过全组集成与部署验收后再发布 v1.0.0，当前基础工程不标记为最终版。
