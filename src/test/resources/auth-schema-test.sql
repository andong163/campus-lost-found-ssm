-- 郭金滔 GJT R1 的 H2 测试夹具：镜像 MySQL 生产表结构（sys_user/sys_role/sys_permission/
-- sys_user_role/sys_role_permission），并种入 4 个角色、权限点与角色-权限映射。
-- 独立于 schema-test.sql，供 AuthFlowTest / AdminInitializerTest 使用。
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  enabled INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(40),
  name VARCHAR(80),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE sys_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(80),
  name VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

INSERT INTO sys_role(code, name) VALUES ('ADMIN', '管理员'), ('EDITOR', '学生'),
  ('REVIEWER', '审核人员'), ('VIEWER', '只读用户');

INSERT INTO sys_permission(code, name) VALUES
  ('user:manage', '用户管理'), ('role:manage', '角色管理'), ('permission:manage', '权限管理'),
  ('article:create', '发布前创建草稿'), ('article:edit', '编辑失物/招领文章'),
  ('article:publish', '发布文章'), ('review:handle', '审核处理'),
  ('claim:handle', '认领处理'), ('claim:resolve', '认领结案'), ('audit:view', '只读审计');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.code = 'ADMIN';

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'EDITOR' AND p.code IN ('article:create', 'article:edit');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'REVIEWER' AND p.code IN ('article:publish', 'review:handle', 'claim:handle');
