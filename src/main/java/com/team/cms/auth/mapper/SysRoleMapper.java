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
    /** 查询指定用户的全部角色代码（关联表联查，见 SysRoleMapper.xml）。 */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 一次性装载多个用户的角色代码，避免用户列表页 N+1 查询。 */
    List<UserRoleRow> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);
}
