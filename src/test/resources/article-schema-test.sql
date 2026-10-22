CREATE TABLE IF NOT EXISTS cms_article (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(200) NOT NULL,
 content CLOB NOT NULL,
 author_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL,
 item_type VARCHAR(10) NOT NULL,
 event_time TIMESTAMP NOT NULL,
 location VARCHAR(100) NOT NULL,
 contact_info VARCHAR(100) NOT NULL,
 resolution_status VARCHAR(20) NOT NULL,
 resolved_at TIMESTAMP NULL,
 published_at TIMESTAMP NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 deleted INT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0
);
DELETE FROM cms_article;
