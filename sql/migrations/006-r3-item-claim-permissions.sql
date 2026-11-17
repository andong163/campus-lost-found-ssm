-- 第三轮集成：新增权限只做登记和追加授权，可重复执行，保留既有自定义授权。
INSERT INTO sys_permission(code, name) VALUES
 ('item:view:any', '查看全站信息管理'),
 ('item:batch', '批量管理信息'),
 ('item:offline:any', '下架全站信息'),
 ('item:delete:any', '删除全站信息'),
 ('item:delete:own', '删除本人信息'),
 ('claim:create', '提交认领申请'),
 ('claim:cancel:own', '撤销本人认领申请'),
 ('claim:handover:own', '确认本人信息交接或找回')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('item:delete:own','claim:create','claim:cancel:own','claim:handover:own')
WHERE r.code='EDITOR'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

-- 全站管理和批量默认仅授予管理员；REVIEWER/VIEWER 不自动获得新写权限。
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('item:view:any','item:batch','item:offline:any','item:delete:any','item:delete:own',
  'claim:create','claim:cancel:own','claim:handover:own')
WHERE r.code='ADMIN'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
