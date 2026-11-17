-- 007：在 006 之后追加寻物联系权限。保留现有认领及自定义角色授权。
INSERT INTO sys_permission(code, name)
SELECT 'item:contact:lost', '单条联系寻物失主'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code='item:contact:lost');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code='item:contact:lost'
WHERE r.code IN ('EDITOR','ADMIN') AND r.deleted=0 AND p.deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
