-- Isolated H2 fixtures. This does NOT validate the MySQL production DDL.
DROP TABLE IF EXISTS test_record;
CREATE TABLE test_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 deleted INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sys_role(id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(40), deleted INT DEFAULT 0);
DELETE FROM sys_role;
INSERT INTO sys_role(code) VALUES ('ADMIN'), ('EDITOR'), ('REVIEWER'), ('VIEWER');
CREATE TABLE IF NOT EXISTS cms_article (
 id BIGINT PRIMARY KEY, item_type VARCHAR(10), event_time TIMESTAMP,
 location VARCHAR(100), contact_info VARCHAR(100), resolution_status VARCHAR(20), resolved_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS cms_claim_request (
 id BIGINT PRIMARY KEY, article_id BIGINT, applicant_id BIGINT, status VARCHAR(20), version INT
);
