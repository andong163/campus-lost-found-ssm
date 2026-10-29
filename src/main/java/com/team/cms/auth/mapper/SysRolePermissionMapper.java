package com.team.cms.auth.mapper;

import com.team.cms.auth.dto.RolePermissionRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 角色-权限关联表读写；复合主键关联表不使用 MP 单主键模型。 */
@Mapper
public interface SysRolePermissionMapper {
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /** 一次性装载全部角色已授予的权限代码。 */
    @Select("SELECT rp.role_id AS roleId, p.code AS permissionCode"
        + " FROM sys_role_permission rp"
        + " JOIN sys_permission p ON p.id = rp.permission_id"
        + " WHERE p.deleted = 0"
        + " ORDER BY rp.role_id, p.code")
    List<RolePermissionRow> selectAllRolePermissionCodes();
}
