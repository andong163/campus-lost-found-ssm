# 第一轮十表字段与索引清单

计划 GMY R1    版本 1.0    2026年10月19日

以下定义从当前 sql/init.sql 提取；SQL 是安装结构的唯一依据。角色种子不包含用户口令。业务接口和权限实现由相应成员交付，已有表结构不代表业务完成。

可变业务实体按实际字段继承 BaseEntity；关系表、审核记录和操作日志没有统一的全部审计列，不强行继承。生成列只由数据库维护。

## sys_user

业务维护人：郭金滔；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| username | `VARCHAR(50) NOT NULL` |
| password_hash | `VARCHAR(255) NOT NULL` |
| display_name | `VARCHAR(80) NOT NULL` |
| enabled | `TINYINT NOT NULL DEFAULT 1` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| deleted | `TINYINT NOT NULL DEFAULT 0` |
| version | `INT NOT NULL DEFAULT 0` |

约束和索引：

- `CONSTRAINT uk_user_username UNIQUE(username)`
- `CONSTRAINT ck_user_enabled CHECK(enabled IN (0,1))`
- `CONSTRAINT ck_user_deleted CHECK(deleted IN (0,1))`

## sys_role

业务维护人：郭金滔；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| code | `VARCHAR(40) NOT NULL` |
| name | `VARCHAR(80) NOT NULL` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| deleted | `TINYINT NOT NULL DEFAULT 0` |
| version | `INT NOT NULL DEFAULT 0` |

约束和索引：

- `CONSTRAINT uk_role_code UNIQUE(code)`
- `CONSTRAINT ck_role_deleted CHECK(deleted IN (0,1))`

## sys_permission

业务维护人：郭金滔；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| code | `VARCHAR(80) NOT NULL` |
| name | `VARCHAR(100) NOT NULL` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| deleted | `TINYINT NOT NULL DEFAULT 0` |
| version | `INT NOT NULL DEFAULT 0` |

约束和索引：

- `CONSTRAINT uk_permission_code UNIQUE(code)`
- `CONSTRAINT ck_permission_deleted CHECK(deleted IN (0,1))`

## sys_user_role

业务维护人：郭金滔；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| user_id | `BIGINT NOT NULL` |
| role_id | `BIGINT NOT NULL` |

约束和索引：

- `PRIMARY KEY(user_id, role_id)`
- `KEY idx_user_role_role(role_id)`
- `CONSTRAINT fk_user_role_user FOREIGN KEY(user_id) REFERENCES sys_user(id)`
- `CONSTRAINT fk_user_role_role FOREIGN KEY(role_id) REFERENCES sys_role(id)`

## sys_role_permission

业务维护人：郭金滔；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| role_id | `BIGINT NOT NULL` |
| permission_id | `BIGINT NOT NULL` |

约束和索引：

- `PRIMARY KEY(role_id, permission_id)`
- `KEY idx_role_permission_permission(permission_id)`
- `CONSTRAINT fk_role_permission_role FOREIGN KEY(role_id) REFERENCES sys_role(id)`
- `CONSTRAINT fk_role_permission_permission FOREIGN KEY(permission_id) REFERENCES sys_permission(id)`

## cms_category

业务维护人：孙睿；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| name | `VARCHAR(80) NOT NULL` |
| parent_id | `BIGINT NULL COMMENT 'Root uses NULL; service checks cycles and duplicate sibling names'` |
| sort_order | `INT NOT NULL DEFAULT 0` |
| enabled | `TINYINT NOT NULL DEFAULT 1` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| deleted | `TINYINT NOT NULL DEFAULT 0` |
| version | `INT NOT NULL DEFAULT 0` |

约束和索引：

- `KEY idx_category_tree(parent_id, deleted, sort_order)`
- `CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES cms_category(id)`
- `CONSTRAINT ck_category_sort CHECK(sort_order >= 0)`
- `CONSTRAINT ck_category_enabled CHECK(enabled IN (0,1))`
- `CONSTRAINT ck_category_deleted CHECK(deleted IN (0,1))`

## cms_article

业务维护人：袁运轩与杨程；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| title | `VARCHAR(200) NOT NULL` |
| content | `LONGTEXT NOT NULL` |
| author_id | `BIGINT NOT NULL` |
| category_id | `BIGINT NOT NULL` |
| status | `VARCHAR(30) NOT NULL DEFAULT 'DRAFT'` |
| item_type | `VARCHAR(10) NOT NULL` |
| event_time | `DATETIME NOT NULL` |
| location | `VARCHAR(100) NOT NULL` |
| contact_info | `VARCHAR(100) NOT NULL` |
| resolution_status | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` |
| resolved_at | `DATETIME NULL` |
| published_at | `DATETIME NULL` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| deleted | `TINYINT NOT NULL DEFAULT 0` |
| version | `INT NOT NULL DEFAULT 0` |

约束和索引：

- `KEY idx_article_public(deleted, status, published_at, id)`
- `KEY idx_article_category(category_id, deleted, status, id)`
- `KEY idx_article_author(author_id, deleted, id)`
- `FULLTEXT KEY idx_article_title(title)`
- `KEY idx_item_filter(deleted, status, item_type, resolution_status, event_time, id)`
- `CONSTRAINT fk_article_author FOREIGN KEY(author_id) REFERENCES sys_user(id)`
- `CONSTRAINT fk_article_category FOREIGN KEY(category_id) REFERENCES cms_category(id)`
- `CONSTRAINT ck_article_status CHECK(status IN ('DRAFT','PENDING_REVIEW','APPROVED','PUBLISHED','REJECTED','OFFLINE'))`
- `CONSTRAINT ck_article_deleted CHECK(deleted IN (0,1))`
- `CONSTRAINT ck_item_type CHECK(item_type IN ('LOST','FOUND'))`
- `CONSTRAINT ck_item_resolution CHECK(resolution_status IN ('OPEN','RESOLVED'))`
- `CONSTRAINT ck_item_resolved_at CHECK( (resolution_status='OPEN' AND resolved_at IS NULL) OR (resolution_status='RESOLVED' AND resolved_at IS NOT NULL))`

## cms_review_record

业务维护人：杨程；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| article_id | `BIGINT NOT NULL` |
| reviewer_id | `BIGINT NOT NULL` |
| decision | `VARCHAR(20) NOT NULL` |
| comment | `VARCHAR(500) NOT NULL DEFAULT ''` |
| article_version | `INT NOT NULL` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |

约束和索引：

- `KEY idx_review_article(article_id, created_at)`
- `KEY idx_review_reviewer(reviewer_id)`
- `CONSTRAINT fk_review_article FOREIGN KEY(article_id) REFERENCES cms_article(id)`
- `CONSTRAINT fk_review_user FOREIGN KEY(reviewer_id) REFERENCES sys_user(id)`
- `CONSTRAINT ck_review_decision CHECK(decision IN ('APPROVED','REJECTED'))`

## cms_claim_request

业务维护人：杨程；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| article_id | `BIGINT NOT NULL` |
| applicant_id | `BIGINT NOT NULL` |
| claim_description | `VARCHAR(1000) NOT NULL` |
| contact_info | `VARCHAR(100) NOT NULL` |
| status | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| reviewer_id | `BIGINT NULL` |
| review_comment | `VARCHAR(500) NOT NULL DEFAULT ''` |
| cancel_reason | `VARCHAR(500) NOT NULL DEFAULT ''` |
| reviewed_at | `DATETIME NULL` |
| completed_at | `DATETIME NULL` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| updated_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| version | `INT NOT NULL DEFAULT 0` |
| active_applicant_id | `BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','APPROVED') THEN applicant_id ELSE NULL END) STORED` |
| approved_article_id | `BIGINT GENERATED ALWAYS AS (CASE WHEN status='APPROVED' THEN article_id ELSE NULL END) STORED` |

约束和索引：

- `UNIQUE KEY uk_claim_active_applicant(article_id, active_applicant_id)`
- `UNIQUE KEY uk_claim_approved_item(approved_article_id)`
- `KEY idx_claim_item(article_id, status, created_at, id)`
- `KEY idx_claim_applicant(applicant_id, created_at, id)`
- `KEY idx_claim_reviewer(reviewer_id, status, created_at)`
- `CONSTRAINT fk_claim_article FOREIGN KEY(article_id) REFERENCES cms_article(id)`
- `CONSTRAINT fk_claim_applicant FOREIGN KEY(applicant_id) REFERENCES sys_user(id)`
- `CONSTRAINT fk_claim_reviewer FOREIGN KEY(reviewer_id) REFERENCES sys_user(id)`
- `CONSTRAINT ck_claim_status CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED'))`
- `CONSTRAINT ck_claim_version CHECK(version >= 0)`

## sys_operation_log

业务维护人：杨程；SQL 总稿与迁移编号由高鸣远维护。

| 字段 | SQL 定义 |
|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| operator_id | `BIGINT NULL` |
| operator_name | `VARCHAR(80) NOT NULL DEFAULT ''` |
| module | `VARCHAR(50) NOT NULL` |
| action | `VARCHAR(80) NOT NULL` |
| target_id | `BIGINT NULL` |
| request_id | `VARCHAR(36) NOT NULL` |
| result | `VARCHAR(20) NOT NULL` |
| elapsed_ms | `BIGINT NOT NULL DEFAULT 0` |
| created_at | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |

约束和索引：

- `KEY idx_log_time(created_at, id)`
- `KEY idx_log_operator(operator_id, created_at)`
- `KEY idx_log_request(request_id)`
- `CONSTRAINT ck_log_result CHECK(result IN ('SUCCESS','FAILURE'))`
- `CONSTRAINT ck_log_elapsed CHECK(elapsed_ms >= 0)`

## 迁移与并发约定

001 与 002 已用于旧九表升级，不再覆盖修改。下一编号 003 保留给郭金滔的权限种子提案，状态为待提交审核；后续编号先向高鸣远登记，再创建实际脚本。没有结构变化时写明无需迁移，不创建空 SQL。

关系和外键已具备。分类循环、有效同级重名、授权和状态需由服务验证；索引不代替服务规则。cms_claim_request 的两个生成列和唯一索引只约束有效重复申请与单个批准，不能当作完整认领实现。

MySQL DDL 可能隐式提交，迁移前停写并备份；业务更新在 InnoDB 事务内处理。公开 Service 经代理调用，按约定锁顺序处理分类、物品和申请；只涉及物品与申请时先锁物品。
