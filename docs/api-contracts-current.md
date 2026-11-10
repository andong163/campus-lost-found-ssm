# 当前实际接口与第三轮接入约定

高鸣远 GMY R3，基于第二轮总合并 `c3f8562`。主文件：[openapi.json](openapi.json)；兼容原导入入口 [openapi.yaml](openapi.yaml)，两者是同一份 OpenAPI 3.0.3 文档，后者使用 YAML 1.2 兼容的 JSON 语法。可导入 ApiFox。第一轮契约文件保留作历史记录，当前接口以本文件、主文件和 Controller 为准。

## 实际范围

当前主文件汇总第三轮集成源码的 13 个实际 Controller、56 个路径、63 个 HTTP 操作；覆盖 system、auth、用户角色权限、分类、本人信息、审核、公开检索、全站管理、批量操作、认领和只读业务日志。已有交接上下文/结案接口按实际代码记录，本次仅补齐写入口防护，不表示第四轮业务已验收。

| 维护人 | 当前片段 | 关键权限/规则 |
| --- | --- | --- |
| 高鸣远 | `/api/system/health`、`/readiness` | 匿名；就绪检查含数据库和必要结构 |
| 郭金滔 | `/api/auth/*`、`/users*`、`/roles*`、`/permissions` | JSESSIONID；用户管理 `user:manage`，角色与权限清单 `role:manage` |
| 孙睿 | `/api/public/categories`、`/api/categories*` | 公开树匿名；管理使用 category:view/add/edit/move/sort/enable/delete |
| 袁运轩 | `/api/items*` | 本人操作限定所有权；公开检索匿名且不含私密字段；全站管理 item:view:any/offline:any/delete:any，批量 item:batch |
| 杨程 | `/api/reviews*`、`/api/items/{itemId}/review-records`、`/api/operation-logs` | 审核保留旧/新权限兼容；认领 claim:create/cancel:own，核实 claim:handle；日志 audit:view |

后续成员在各自分支交付新接口和片段，高鸣远集成时执行 `./scripts/export-api.ps1` 汇总，不要求各人同时手工修改整份主文件。集成契约测试核对路径数量、每路径操作数量、权限、请求结构及 CSRF 标注，第三轮总基线须同时汇总新旧接口。

## 请求、会话和响应

基础地址为 `http://127.0.0.1:端口/cms`，OpenAPI 里的路径均带 `/api`。全部成功返回 HTTP 200 及 `{"code":200,"message":"success","data":...}`，分页 data 为 page、size、total、records。data 可以是对象、数组、字符串或 null，不能一律按对象解析。每次请求返回 X-Request-Id；错误 HTTP 状态与 code 一致，不显示 SQL、堆栈或口令。

登录以 JSESSIONID 保存身份，重复登录换发会话；登录后 GET `/api/auth/csrf` 的 data 是令牌**字符串**，响应 no-store。测试或页面把该字符串放入 X-CSRF-Token，不能使用 data.token，不应共享或提交会话 Cookie/令牌。

第三轮集成的服务端 CSRF 检查覆盖物品创建/修改/状态/批量命令、审核写入口、认领申请/核实/取消/已有结案入口、用户启停/角色分配和角色写操作。缺失、错误或其他会话的令牌均返回 403。分类写接口和退出接口当前没有服务端令牌校验，主文件如实标为 false；GET 只读请求无需写令牌。导出器按已核验的 Controller 方法维护清单，不再按 URL 前缀猜测保护状态；新增入口须补充实际负向请求验证后再标记。

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

已有库按 003 → 004 → 005 → 006 顺序升级权限元数据；`scripts/upgrade-db.ps1` 已串接这些增量脚本，新库的 init.sql 同步包含这些权限。006 仅追加角色授权，默认 EDITOR 获得本人删除与认领写权限，全站管理/批量仅 ADMIN 默认获得，REVIEWER/VIEWER 不自动获得新增写权限。原有已发布脚本不回写。GMY R3 使用唯一的 `cms_gmy_r3_十二位编号` 专用 MySQL 库，测试角色和注册数据仅存在于该库。完整准备、部署、运行和清理说明见 [gmy-r3.md](gmy-r3.md)。真实业务库不会被测试流程覆盖。

## 第三轮公开检索、管理与认领

| 接口 | 规则 |
| --- | --- |
| GET `/api/public/items`、`/{id}` | 匿名；分类含可见后代，禁用祖先隐藏子树；DTO 不含联系方式、作者身份和认领证据 |
| GET `/api/items` | `item:view:any`；返回授权后的管理视图 |
| POST `/api/items/batch-publish`、`/batch-offline`、`/batch-delete` | `item:batch` 及各动作权限；ids 与 versions 必须完整匹配，整批事务回滚 |
| POST `/api/items/{itemId}/claims` | `claim:create`；仅已发布且未结案 FOUND；禁止认领本人信息 |
| POST `/api/claims/{claimId}/decision` | `claim:handle`；禁止核实本人申请或本人发布物品的申请；同物品至多一条 APPROVED |
| POST `/api/claims/{claimId}/cancel` | `claim:cancel:own`，仍须核验申请人；不能撤销已结案申请 |
| GET `/api/items/{itemId}/found-contact` | 依 ClaimReadPolicy 授权；未通过核实及无关用户不能读取 |

申请通过不改变物品 OPEN 状态，也不填写 completedAt；交接完成是后续独立业务步骤。VIEWER 没有新写权限；服务层再次验证申请、撤销和已有确认入口的写权限，身份降为只读后不得凭原所有权继续写入。
