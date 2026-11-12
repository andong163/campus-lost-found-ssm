-- =====================================================================
-- 规划权限码统一授权草案（GJT R4 提交，尚未执行）
-- ---------------------------------------------------------------------
-- 文件位置：docs/ 而非 sql/migrations/，因为团队约定新迁移编号必须先向
--           高鸣远登记（docs/database-schema.md:249），登记后再改名放到
--           sql/migrations/ 下（建议 004-permission-alignment.sql）。
-- 前置条件：sql/init.sql 已建表，sql/migrations/003-auth-permission-seed.sql
--           已执行。
-- 约束：本脚本只新增规划码及其授权，不修改 003 已发布的 10 个权限码，
--       符合 docs/r1-total-integration.md:22「不回写已发布的 003」。
-- 待确认：第 3 节的默认授权矩阵需要孙睿、袁运轩、杨程各自确认本模块业务
--         是否需要额外权限码，以及只读用户是否允许查看分类树。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1 规划权限码入库（41 个，来自 com.team.cms.common.security.PermissionCodes）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (code, name) VALUES
    ('user:view',          '查看用户'),
    ('user:edit',          '编辑用户'),
    ('user:disable',       '停用用户'),
    ('user:role',          '分配用户角色'),
    ('role:view',          '查看角色'),
    ('role:edit',          '编辑角色'),
    ('permission:view',    '查看权限'),
    ('permission:edit',    '编辑权限'),
    ('category:view',      '查看分类'),
    ('category:add',       '新增分类'),
    ('category:edit',      '编辑分类'),
    ('category:move',      '移动分类'),
    ('category:sort',      '调整分类排序'),
    ('category:enable',    '启用停用分类'),
    ('category:delete',    '删除分类'),
    ('item:create',        '发布信息'),
    ('item:view:own',      '查看本人信息'),
    ('item:view:any',      '查看他人信息'),
    ('item:edit:own',      '编辑本人信息'),
    ('item:edit:any',      '编辑他人信息'),
    ('item:delete:own',    '删除本人信息'),
    ('item:delete:any',    '删除他人信息'),
    ('item:submit',        '提交信息审核'),
    ('item:withdraw',      '撤回提交'),
    ('item:publish',       '发布上架'),
    ('item:offline:own',   '下架本人信息'),
    ('item:offline:any',   '下架他人信息'),
    ('item:batch',         '信息批量操作'),
    ('item:contact:lost',  '查看失物联系方式'),
    ('item:contact:found', '查看招领联系方式'),
    ('item:resolve:own',   '标记本人信息已解决'),
    ('review:view',        '查看审核队列'),
    ('review:decide',      '审核通过或驳回'),
    ('claim:create',       '提交认领申请'),
    ('claim:view:own',     '查看本人认领'),
    ('claim:view:any',     '查看全部认领'),
    ('claim:review',       '处理认领申请'),
    ('claim:cancel:own',   '撤销本人认领'),
    ('claim:cancel:any',   '撤销他人认领'),
    ('claim:handover:own', '确认本人信息交接'),
    ('audit:view',         '查看操作日志')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------------------------------------------------------------------
-- 2 授权方式：按角色代码 + 权限码集合建立映射
--    使用 INSERT ... SELECT 避免依赖自增主键的具体取值。
-- ---------------------------------------------------------------------

-- 2.1 管理员：授予全部规划码。
--     PermissionAspect 对 ADMIN 直接放行，这里落库是为了页面勾选状态与
--     「权限维护」页面显示一致，不落库也能工作。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'ADMIN'
  AND r.deleted = 0
  AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 2.2 学生（EDITOR）：只能操作本人信息与本人认领，可以浏览分类。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'category:view',
    'item:create', 'item:view:own', 'item:edit:own', 'item:delete:own',
    'item:submit', 'item:withdraw', 'item:offline:own',
    'item:contact:lost', 'item:contact:found', 'item:resolve:own',
    'claim:create', 'claim:view:own', 'claim:cancel:own', 'claim:handover:own'
)
WHERE r.code = 'EDITOR'
  AND r.deleted = 0
  AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 2.3 审核员（REVIEWER）：审核队列、审核决定、全部认领查看与处理。
--     003 已给 REVIEWER 授了 review:handle、article:publish、claim:handle，
--     这里补充规划码，形成「旧码继续可用 + 新码可用」的过渡状态。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'category:view',
    'item:view:any', 'item:offline:any',
    'item:contact:lost', 'item:contact:found',
    'review:view', 'review:decide',
    'claim:view:any', 'claim:review', 'claim:cancel:any'
)
WHERE r.code = 'REVIEWER'
  AND r.deleted = 0
  AND p.deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 2.4 只读用户（VIEWER）：默认不授予任何权限码，保持「只读=未经授权即不可见」。
--     如果团队希望只读用户能看到分类树，需要额外确认，见第 3 节待确认项。
--     （本段刻意留空，避免默认放行。）

-- ---------------------------------------------------------------------
-- 3 待确认事项
-- ---------------------------------------------------------------------
-- a) 只读用户是否允许 GET /api/categories/tree（category:view）？
--    当前按「不授予」处理，与其他模块的只读语义保持一致。
-- b) 学生是否需要 item:view:any（查看他人已发布信息）？
--    公开检索接口如果走匿名白名单，则不需要；若要求登录后检索，则需要授予。
-- c) 003 中的 article:create / article:edit / article:publish 与规划的
--    item:create / item:publish 语义重叠，是否在 005 中做一次重命名合并，
--    由高鸣远在总体验收时决定。
-- d) 本脚本执行后，docs/gjt-r4-identity-guide.md 第 4 节的权限矩阵需要
--    重跑 PermissionMatrixTest 并同步更新预期值。
