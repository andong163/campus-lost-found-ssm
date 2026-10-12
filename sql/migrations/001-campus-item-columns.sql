-- Existing CMS -> campus schema. The upgrade tool first requires cms_article to be empty.
-- Do not fabricate type, event time or contact information for existing articles.
ALTER TABLE cms_article
 ADD COLUMN item_type VARCHAR(10) NOT NULL,
 ADD COLUMN event_time DATETIME NOT NULL,
 ADD COLUMN location VARCHAR(100) NOT NULL,
 ADD COLUMN contact_info VARCHAR(100) NOT NULL,
 ADD COLUMN resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 ADD COLUMN resolved_at DATETIME NULL,
 ADD KEY idx_item_filter(deleted, status, item_type, resolution_status, event_time, id),
 ADD CONSTRAINT ck_item_type CHECK(item_type IN ('LOST','FOUND')),
 ADD CONSTRAINT ck_item_resolution CHECK(resolution_status IN ('OPEN','RESOLVED')),
 ADD CONSTRAINT ck_item_resolved_at CHECK(
   (resolution_status='OPEN' AND resolved_at IS NULL) OR
   (resolution_status='RESOLVED' AND resolved_at IS NOT NULL));
