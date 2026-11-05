# 当前实际接口与第三轮接入约定

高鸣远 GMY R3，基于第二轮总合并 `c3f8562`。主文件：[openapi.json](openapi.json)；兼容原导入入口 [openapi.yaml](openapi.yaml)，两者是同一份 OpenAPI 3.0.3 文档，后者使用 YAML 1.2 兼容的 JSON 语法。可导入 ApiFox。第一轮契约文件保留作历史记录，当前接口以本文件、主文件和 Controller 为准。

## 实际范围

当前主文件汇总 10 个实际 Controller、41 个路径、45 个 HTTP 操作；覆盖 system、auth、用户角色权限、分类、本人信息、审核和只读业务日志。没有将第三轮尚未交付的公开物品检索、批量管理、认领申请或第四轮交接结案接口写成可调用功能。

| 维护人 | 当前片段 | 关键权限/规则 |
| --- | --- | --- |
| 高鸣远 | `/api/system/health`、`/readiness` | 匿名；就绪检查含数据库和必要结构 |
| 郭金滔 | `/api/auth/*`、`/users*`、`/roles*`、`/permissions` | JSESSIONID；用户管理 `user:manage`，角色与权限清单 `role:manage` |
| 孙睿 | `/api/public/categories`、`/api/categories*` | 公开树匿名；管理使用 category:view/add/edit/move/sort/enable/delete |
| 袁运轩 | `/api/items*` | item:create/view:own/edit:own/submit/withdraw/offline:own，限定本人；字段和状态由统一服务校验 |
| 杨程 | `/api/reviews*`、`/api/items/{itemId}/review-records`、`/api/operation-logs` | 原路由 review:handle；兼容路由 review:view/review:decide/item:publish；日志 audit:view |

后续成员在各自分支交付新接口和片段，高鸣远集成时执行 `./scripts/export-api.ps1` 汇总，不要求各人同时手工修改整份主文件。契约测试核对已记录路由、权限及请求结构仍然存在；允许新接口先以模块片段交付。

## 请求、会话和响应

基础地址为 `http://127.0.0.1:端口/cms`，OpenAPI 里的路径均带 `/api`。全部成功返回 HTTP 200 及 `{"code":200,"message":"success","data":...}`，分页 data 为 page、size、total、records。data 可以是对象、数组、字符串或 null，不能一律按对象解析。每次请求返回 X-Request-Id；错误 HTTP 状态与 code 一致，不显示 SQL、堆栈或口令。

登录以 JSESSIONID 保存身份，重复登录换发会话；登录后 GET `/api/auth/csrf` 的 data 是令牌**字符串**，响应 no-store。测试或页面把该字符串放入 X-CSRF-Token，不能使用 data.token，不应共享或提交会话 Cookie/令牌。

截至第二轮基线，令牌检查实际覆盖物品创建/修改/状态命令和两个审核决定入口及审核发布入口。分类管理、身份管理等其他写接口的扩展覆盖属于郭金滔第三轮工作。主文件逐接口记录 `x-csrf-enforced`，本轮没有把“客户端携带了令牌”误写成“服务器已校验所有写接口”。

常见状态：400 输入或格式非法，401 未登录/失效，403 权限或 CSRF 不符，404 不存在或隐藏他人内容，409 重名/状态/版本/引用冲突，500 非预期错误，503 数据库依赖不可用。主文件列出了公共错误响应契约，不代表每个接口都能触发所有状态。日期为 Asia/Shanghai 本地 ISO 日期时间，不带时区偏移；主文件未把它误标为要求时区的 RFC3339 字段。

## 审核与状态兼容

本人草稿创建后 version=0；修改和合法状态转换递增 version。提审、撤回等写请求携带当前 version。旧版本返回 409；待审不能直接编辑，须先撤回；已发布先下架转草稿再修改。

| 接口 | 请求 | 响应/权限 |
| --- | --- | --- |
| GET `/api/reviews` | page、size、keyword、itemType、categoryId | `PageResult<ReviewTaskView>`；review:handle；不含正文或联系方式 |
| GET `/api/reviews/pending` | page、size | 授权后的私有信息分页；review:view；排除本人待审 |
| POST `/api/reviews/{itemId}/decision` | version、decision=APPROVED/REJECTED、comment | data=null；review:handle；要求 CSRF |
| POST `/api/reviews/{id}/approve` 或 `/reject` | version、comment | 私有详情；review:decide；决定由路由提供，要求 CSRF |
| GET `/api/reviews/history` | articleId、decision、page、size | 审核历史；review:handle |
| GET `/api/items/{id}/reviews` | 本人信息 ID | 作者审核意见；item:view:own，仍验证本人 |
| GET `/api/items/{itemId}/review-records` | 信息 ID | 作者或有审核权限者；Service 验证所有权/权限 |

驳回必须有意见，不能审核本人信息；状态与审核记录同事务，旧/新路由均进入杨程服务。APPROVED 只是审核通过，不等于 PUBLISHED，更不等于已归还或结案。私有详情不能用于公开列表或公开详情。

## 迁移与环境

已有库按 003 → 004 → 005 顺序升级权限元数据；新库的 init.sql 已包含这些权限。原有已发布脚本不回写。GMY R3 使用唯一的 `cms_gmy_r3_十二位编号` 专用 MySQL 库，测试角色和注册数据仅存在于该库。完整准备、部署、运行和清理说明见 [gmy-r3.md](gmy-r3.md)。真实业务库不会被测试流程覆盖。
