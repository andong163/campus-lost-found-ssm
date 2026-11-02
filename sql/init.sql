-- MySQL 8.0.16+ baseline. No DROP/TRUNCATE, no real accounts or passwords.
-- CREATE TABLE IF NOT EXISTS is for first installation, not schema migration.
CREATE DATABASE IF NOT EXISTS cms_content CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cms_content;

CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 display_name VARCHAR(80) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT uk_user_username UNIQUE(username),
 CONSTRAINT ck_user_enabled CHECK(enabled IN (0,1)),
 CONSTRAINT ck_user_deleted CHECK(deleted IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_role (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(80) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT uk_role_code UNIQUE(code),
 CONSTRAINT ck_role_deleted CHECK(deleted IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_permission (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(80) NOT NULL,
 name VARCHAR(100) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT uk_permission_code UNIQUE(code),
 CONSTRAINT ck_permission_deleted CHECK(deleted IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_user_role (
 user_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 PRIMARY KEY(user_id, role_id),
 KEY idx_user_role_role(role_id),
 CONSTRAINT fk_user_role_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_user_role_role FOREIGN KEY(role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_role_permission (
 role_id BIGINT NOT NULL,
 permission_id BIGINT NOT NULL,
 PRIMARY KEY(role_id, permission_id),
 KEY idx_role_permission_permission(permission_id),
 CONSTRAINT fk_role_permission_role FOREIGN KEY(role_id) REFERENCES sys_role(id),
 CONSTRAINT fk_role_permission_permission FOREIGN KEY(permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cms_category (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 parent_id BIGINT NULL COMMENT 'Root uses NULL; service checks cycles and duplicate sibling names',
 sort_order INT NOT NULL DEFAULT 0,
 enabled TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 KEY idx_category_tree(parent_id, deleted, sort_order),
 CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES cms_category(id),
 CONSTRAINT ck_category_sort CHECK(sort_order >= 0),
 CONSTRAINT ck_category_enabled CHECK(enabled IN (0,1)),
 CONSTRAINT ck_category_deleted CHECK(deleted IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cms_article (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(200) NOT NULL,
 content LONGTEXT NOT NULL,
 author_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 item_type VARCHAR(10) NOT NULL,
 event_time DATETIME NOT NULL,
 location VARCHAR(100) NOT NULL,
 contact_info VARCHAR(100) NOT NULL,
 resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 resolved_at DATETIME NULL,
 published_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 KEY idx_article_public(deleted, status, published_at, id),
 KEY idx_article_category(category_id, deleted, status, id),
 KEY idx_article_author(author_id, deleted, id),
 FULLTEXT KEY idx_article_title(title),
 KEY idx_item_filter(deleted, status, item_type, resolution_status, event_time, id),
 CONSTRAINT fk_article_author FOREIGN KEY(author_id) REFERENCES sys_user(id),
 CONSTRAINT fk_article_category FOREIGN KEY(category_id) REFERENCES cms_category(id),
 CONSTRAINT ck_article_status CHECK(status IN ('DRAFT','PENDING_REVIEW','APPROVED','PUBLISHED','REJECTED','OFFLINE')),
 CONSTRAINT ck_article_deleted CHECK(deleted IN (0,1)),
 CONSTRAINT ck_item_type CHECK(item_type IN ('LOST','FOUND')),
 CONSTRAINT ck_item_resolution CHECK(resolution_status IN ('OPEN','RESOLVED')),
 CONSTRAINT ck_item_resolved_at CHECK(
   (resolution_status='OPEN' AND resolved_at IS NULL) OR
   (resolution_status='RESOLVED' AND resolved_at IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cms_review_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 article_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,
 decision VARCHAR(20) NOT NULL,
 comment VARCHAR(500) NOT NULL DEFAULT '',
 article_version INT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_review_article(article_id, created_at),
 KEY idx_review_reviewer(reviewer_id),
 CONSTRAINT fk_review_article FOREIGN KEY(article_id) REFERENCES cms_article(id),
 CONSTRAINT fk_review_user FOREIGN KEY(reviewer_id) REFERENCES sys_user(id),
 CONSTRAINT ck_review_decision CHECK(decision IN ('APPROVED','REJECTED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cms_claim_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 article_id BIGINT NOT NULL,
 applicant_id BIGINT NOT NULL,
 claim_description VARCHAR(1000) NOT NULL,
 contact_info VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 reviewer_id BIGINT NULL,
 review_comment VARCHAR(500) NOT NULL DEFAULT '',
 cancel_reason VARCHAR(500) NOT NULL DEFAULT '',
 reviewed_at DATETIME NULL,
 completed_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0,
 active_applicant_id BIGINT GENERATED ALWAYS AS
   (CASE WHEN status IN ('PENDING','APPROVED') THEN applicant_id ELSE NULL END) STORED,
 approved_article_id BIGINT GENERATED ALWAYS AS
   (CASE WHEN status='APPROVED' THEN article_id ELSE NULL END) STORED,
 UNIQUE KEY uk_claim_active_applicant(article_id, active_applicant_id),
 UNIQUE KEY uk_claim_approved_item(approved_article_id),
 KEY idx_claim_item(article_id, status, created_at, id),
 KEY idx_claim_applicant(applicant_id, created_at, id),
 KEY idx_claim_reviewer(reviewer_id, status, created_at),
 CONSTRAINT fk_claim_article FOREIGN KEY(article_id) REFERENCES cms_article(id),
 CONSTRAINT fk_claim_applicant FOREIGN KEY(applicant_id) REFERENCES sys_user(id),
 CONSTRAINT fk_claim_reviewer FOREIGN KEY(reviewer_id) REFERENCES sys_user(id),
 CONSTRAINT ck_claim_status CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED')),
 CONSTRAINT ck_claim_version CHECK(version >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_operation_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 operator_id BIGINT NULL,
 operator_name VARCHAR(80) NOT NULL DEFAULT '',
 module VARCHAR(50) NOT NULL,
 action VARCHAR(80) NOT NULL,
 target_id BIGINT NULL,
 request_id VARCHAR(36) NOT NULL,
 result VARCHAR(20) NOT NULL,
 elapsed_ms BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_log_time(created_at, id),
 KEY idx_log_operator(operator_id, created_at),
 KEY idx_log_request(request_id),
 CONSTRAINT ck_log_result CHECK(result IN ('SUCCESS','FAILURE')),
 CONSTRAINT ck_log_elapsed CHECK(elapsed_ms >= 0)
) ENGINE=InnoDB;

-- Only non-sensitive role metadata; user module provisions real test users locally.
INSERT INTO sys_role(code, name) VALUES
 ('ADMIN', '管理员'), ('EDITOR', '学生'), ('REVIEWER', '审核人员'), ('VIEWER', '只读用户')
ON DUPLICATE KEY UPDATE code = VALUES(code);

-- 第二轮权限元数据；升级已有环境仍按编号执行迁移。
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

-- GMY 第二轮集成：登记完整分类管理权限。先执行 003、004，再执行本脚本。
-- 仅 ADMIN 默认获得分类管理；学生和审核员不自动获得分类写权限。
INSERT INTO sys_permission(code, name) VALUES
 ('category:view', '查看分类管理树'),
 ('category:add', '新增分类'),
 ('category:edit', '修改分类名称'),
 ('category:move', '移动分类'),
 ('category:sort', '调整分类排序'),
 ('category:enable', '启停分类'),
 ('category:delete', '逻辑删除分类')
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN
 ('category:view','category:add','category:edit','category:move','category:sort','category:enable','category:delete')
WHERE r.code='ADMIN'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
