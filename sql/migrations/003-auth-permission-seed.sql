-- 郭金滔 GJT R1 权限点种子与角色-权限映射（草案，编号由高鸣远统一分配，暂定 003）
-- 角色种子已在 init.sql 中，此处只种权限点并建立角色→权限映射。
-- 幂等：重复执行不产生重复行。

INSERT INTO sys_permission(code, name) VALUES
  ('user:manage',       '用户管理'),
  ('role:manage',       '角色管理'),
  ('permission:manage', '权限管理'),
  ('article:create',    '发布前创建草稿'),
  ('article:edit',      '编辑失物/招领文章'),
  ('article:publish',   '发布文章'),
  ('review:handle',     '审核处理'),
  ('claim:handle',      '认领处理'),
  ('claim:resolve',     '认领结案'),
  ('audit:view',        '只读审计')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 角色 → 权限映射
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
  'user:manage', 'role:manage', 'permission:manage',
  'article:create', 'article:edit', 'article:publish',
  'review:handle', 'claim:handle', 'claim:resolve', 'audit:view'
)
WHERE r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('article:create', 'article:edit')
WHERE r.code = 'EDITOR'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('article:publish', 'review:handle', 'claim:handle')
WHERE r.code = 'REVIEWER'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- VIEWER：无写权限，不映射任何权限点。
