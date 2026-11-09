-- YYX R4 必要依赖提案；006 在当前远程编号表中空闲，整合时由高鸣远确认。
-- 只追加权限元数据；003/004/005 之后执行。可重复执行，不覆盖已有角色的自定义授权。
-- 全新数据库执行 init.sql 后仍需执行本文件。无表结构变更，无业务数据修改。
INSERT INTO sys_permission(code,name)
SELECT seed.code, seed.name FROM (
 SELECT 'item:contact:lost' AS code, '单条联系寻物失主' AS name
 UNION ALL SELECT 'claim:create','提交认领申请'
 UNION ALL SELECT 'claim:view:own','查询本人认领历史'
 UNION ALL SELECT 'claim:cancel:own','撤销本人认领'
 UNION ALL SELECT 'item:resolve:own','确认本人寻物找回'
 UNION ALL SELECT 'claim:handover:own','确认本人招领实际交接'
) seed WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.code=seed.code);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.code='EDITOR' OR (r.code='ADMIN' AND p.code IN ('item:contact:lost','item:resolve:own')))
 AND r.deleted=0 AND p.deleted=0
 AND p.code IN ('item:contact:lost','claim:create','claim:view:own','claim:cancel:own',
                'item:resolve:own','claim:handover:own')
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
