package com.team.cms.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatabaseProbeMapper {
    @Select("SELECT 1") int ping();
    @Select("SELECT COUNT(*) FROM sys_role WHERE deleted = 0 AND code IN ('ADMIN','EDITOR','REVIEWER','VIEWER')") long countRoles();
}
