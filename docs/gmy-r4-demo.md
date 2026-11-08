# 第四轮接口演示与报告取证步骤

先使用 [OpenAPI](openapi.json) 导入 ApiFox，将 baseUrl 设置为本机 Tomcat 的 /cms 上下文。使用本机专用演示账号，不在共享环境或截图中保存密码、Cookie、令牌、联系方式和认领证据。HTTP 请求下列用 {id} 表示实际响应返回的编号，不能硬编码其他同学的数据库 ID。

## FOUND 发布到交接

1. 作者以 EDITOR 登录，GET /api/auth/csrf，取 data 字符串作为本会话 X-CSRF-Token。
2. 创建 FOUND 草稿，提审；另一个审核人员批准并发布，按每次返回的最新 version 发命令。
3. 游客 GET /api/public/items 和 /{id}，确认无联系方式、作者 ID 或认领证据。
4. 另一学生 POST /api/items/{itemId}/claims，核实人员 POST /api/claims/{claimId}/decision 批准；确认物品仍 OPEN，申请 APPROVED 且 completedAt 为空。
5. 作者 POST /api/items/{itemId}/resolve，JSON 包含当前 version 和实际 claimId。核对物品 RESOLVED/resolvedAt/版本递增、申请 COMPLETED/completedAt、其他待核实申请 CANCELLED。
6. 重复确认应 409，非作者应 403，结案后新认领应 409。授权管理员查询 CLAIM_RESOLVE 审计，日志只保留必要结构化字段。

## LOST 本人找回

LOST 经审核发布后，由作者携带同会话令牌与当前 version 调用 resolve；核对 RESOLVED 和 resolvedAt。他人操作应 403，重复操作应 409，不应要求虚构 FOUND 认领申请。

## 自动回滚与安装升级证据

执行 scripts/test-gmy-r4.ps1 取得本次 r4-verification.json。该工具在专用库通过受控触发器使交接第二步失败，验证申请未提前 COMPLETED、物品仍 OPEN、时间和版本未改变、其他申请仍 PENDING，移除触发器后再验证成功。不要在个人真实数据库手工创建该触发器。

旧库探针在另一专用库还原九表结构，实际调用同一升级函数，验证非空拒绝、部分升级拒绝、空库升级及重复执行。两库均有归属标记并自动清理。

报告取证保留构建结果、SQL 日志、接口状态码/公开字段与事务前后状态的真实截图，遮盖隐私字段。完整自动化原始记录保存在本次 .local 验收目录；可共享脱敏结果为 docs/gmy-r4-verification.json。工具记录不代替本人课堂笔记、人工审阅和思考题。
