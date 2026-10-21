-- 分类模块独立 H2 测试夹具；生产 DDL 以 sql/init.sql 为准，本文件不验证 MySQL 生产结构。
DROP TABLE IF EXISTS cms_category;
CREATE TABLE cms_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  parent_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES cms_category(id)
);
