-- 杨程 review/claim/audit 的 H2 测试夹具。
-- 镜像 MySQL 生产表结构的关键列与约束；cms_claim_request 使用 H2 生成列模拟两个唯一索引。
-- 每个测试方法前由 @Sql 重建，数据可重复、可清理。
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS cms_claim_request;
DROP TABLE IF EXISTS cms_review_record;
DROP TABLE IF EXISTS cms_article;
DROP TABLE IF EXISTS cms_category;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL DEFAULT '',
  display_name VARCHAR(80) NOT NULL,
  enabled INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE cms_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  parent_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0
);

CREATE TABLE cms_article (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  author_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  item_type VARCHAR(10) NOT NULL,
  event_time TIMESTAMP NOT NULL,
  location VARCHAR(100) NOT NULL,
  contact_info VARCHAR(100) NOT NULL,
  resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  resolved_at TIMESTAMP NULL,
  published_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT ck_article_status CHECK (status IN
    ('DRAFT','PENDING_REVIEW','APPROVED','PUBLISHED','REJECTED','OFFLINE')),
  CONSTRAINT ck_article_item_type CHECK (item_type IN ('LOST','FOUND')),
  CONSTRAINT ck_article_resolution CHECK (resolution_status IN ('OPEN','RESOLVED'))
);

CREATE TABLE cms_review_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  decision VARCHAR(20) NOT NULL,
  comment VARCHAR(500) NOT NULL DEFAULT '',
  article_version INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED','REJECTED'))
);

CREATE TABLE cms_claim_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  applicant_id BIGINT NOT NULL,
  claim_description VARCHAR(1000) NOT NULL,
  contact_info VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT NULL,
  review_comment VARCHAR(500) NOT NULL DEFAULT '',
  cancel_reason VARCHAR(500) NOT NULL DEFAULT '',
  reviewed_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  active_applicant_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status IN ('PENDING','APPROVED') THEN applicant_id ELSE NULL END),
  approved_article_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status = 'APPROVED' THEN article_id ELSE NULL END),
  CONSTRAINT uk_claim_active_applicant UNIQUE (article_id, active_applicant_id),
  CONSTRAINT uk_claim_approved_item UNIQUE (approved_article_id),
  CONSTRAINT ck_claim_status CHECK (status IN
    ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED'))
);

CREATE TABLE sys_operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NULL,
  operator_name VARCHAR(80) NOT NULL DEFAULT '',
  module VARCHAR(50) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_id BIGINT NULL,
  request_id VARCHAR(36) NOT NULL,
  result VARCHAR(20) NOT NULL,
  elapsed_ms BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_log_result CHECK (result IN ('SUCCESS','FAILURE'))
);

INSERT INTO sys_user(id, username, display_name) VALUES
  (1, 'author01', '张同学'),
  (2, 'applicant01', '李同学'),
  (3, 'reviewer01', '王审核'),
  (4, 'student04', '赵同学'),
  (5, 'admin01', '系统管理员');

INSERT INTO cms_category(id, name, parent_id, sort_order, enabled) VALUES
  (1, '电子产品', NULL, 1, 1),
  (2, '手机', 1, 1, 1),
  (3, '停用根类', NULL, 2, 0),
  (4, '停用根类子类', 3, 1, 1),
  (5, '生活用品', NULL, 3, 1);

INSERT INTO cms_article(id, title, content, author_id, category_id, status, item_type,
    event_time, location, contact_info, resolution_status, resolved_at, published_at, version) VALUES
  (1, '拾到黑色雨伞', '在图书馆三楼拾到一把黑色长柄雨伞，请失主联系我。', 1, 2, 'PUBLISHED',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '图书馆三楼', '13800000001', 'OPEN', NULL,
   CURRENT_TIMESTAMP, 2),
  (2, '丢失校园卡', '在第二食堂丢失校园卡一张，卡套为蓝色，请拾到者联系。', 1, 2, 'PUBLISHED',
   'LOST', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '第二食堂', '13800000001', 'OPEN', NULL,
   CURRENT_TIMESTAMP, 2),
  (3, '草稿物品', '这是一条尚未提交审核的草稿信息，用于验证状态流转。', 1, 2, 'DRAFT',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '体育馆', '13800000001', 'OPEN', NULL,
   NULL, 0),
  (4, '待审核物品', '这是一条等待审核的招领信息，内容完整，可以批准或驳回。', 1, 2, 'PENDING_REVIEW',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '教学楼A', '13800000001', 'OPEN', NULL,
   NULL, 1),
  (5, '审核员自己的待审物品', '审核员自己发布的信息，用于验证自审回避规则。', 3, 2, 'PENDING_REVIEW',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '教学楼B', '13800000003', 'OPEN', NULL,
   NULL, 1),
  (6, '审核员发布的招领', '审核员发布的招领信息，用于验证利益相关回避。', 3, 2, 'PUBLISHED',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '实验楼', '13800000003', 'OPEN', NULL,
   CURRENT_TIMESTAMP, 2),
  (7, '停用分类下的招领', '分类已经停用，新增认领必须被分类可用性检查拒绝。', 1, 3, 'PUBLISHED',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '校门口', '13800000001', 'OPEN', NULL,
   CURRENT_TIMESTAMP, 2),
  (8, '已结案的招领', '已经完成交接的招领信息，保留公开历史但禁止新增认领。', 1, 2, 'PUBLISHED',
   'FOUND', DATEADD('DAY', -2, CURRENT_TIMESTAMP), '图书馆', '13800000001', 'RESOLVED',
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2),
  (9, '停用分类下的草稿', '草稿位于已停用分类，提交审核时必须被分类规则拒绝。', 1, 3, 'DRAFT',
   'FOUND', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '校门口', '13800000001', 'OPEN', NULL,
   NULL, 0);
