package com.team.cms.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.auth.dto.UserRoleRow;
import com.team.cms.auth.entity.SysRole;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    /** 管理员数量检查与角色替换共用的事务锁；必须在读取用户之前获取。 */
    @org.apache.ibatis.annotations.Select("SELECT id FROM sys_role WHERE code='ADMIN' AND deleted=0 FOR UPDATE")
    Long lockAdminRole();

    /** 查询指定用户的全部角色代码（关联表联查，见 SysRoleMapper.xml）。 */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 一次性装载多个用户的角色代码，避免用户列表页 N+1 查询。 */
    List<UserRoleRow> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);
}
