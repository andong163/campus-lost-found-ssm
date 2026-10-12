package com.team.cms.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatabaseProbeMapper {
    @Select("SELECT 1") int ping();
    @Select("SELECT COUNT(*) FROM sys_role WHERE deleted = 0 AND code IN ('ADMIN','EDITOR','REVIEWER','VIEWER')") long countRoles();
    // Empty subqueries validate required tables/columns without reading business records.
    @Select("SELECT COUNT(*) FROM (SELECT item_type,event_time,location,contact_info,resolution_status,resolved_at FROM cms_article WHERE 1=0) item_schema")
    long checkItemSchema();
    @Select("SELECT COUNT(*) FROM (SELECT article_id,applicant_id,status,version FROM cms_claim_request WHERE 1=0) claim_schema")
    long checkClaimSchema();
}
