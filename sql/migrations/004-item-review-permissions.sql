-- YYX R2: activate the shared item/review permission codes without rewriting 003.
-- Idempotent; existing legacy grants remain for modules that still use them.
INSERT INTO sys_permission(code, name) VALUES
 ('item:create', '创建本人信息'),
 ('item:view:own', '查看本人信息'),
 ('item:edit:own', '编辑本人信息'),
 ('item:submit', '提交本人信息审核'),
 ('item:withdraw', '撤回本人待审信息'),
 ('item:offline:own', '下架本人信息'),
 ('item:publish', '发布审核通过信息'),
 ('review:view', '查看信息审核待办'),
 ('review:decide', '批准或驳回信息')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('item:create','item:view:own','item:edit:own','item:submit','item:withdraw','item:offline:own')
WHERE r.code='EDITOR'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('review:view','review:decide','item:publish')
WHERE r.code='REVIEWER'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('item:create','item:view:own','item:edit:own','item:submit','item:withdraw',
  'item:offline:own','item:publish','review:view','review:decide')
WHERE r.code='ADMIN'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
