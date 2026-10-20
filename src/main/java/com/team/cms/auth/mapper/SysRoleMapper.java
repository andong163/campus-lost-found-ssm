package com.team.cms.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.auth.entity.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    /** 查询指定用户的全部角色代码（关联表联查，见 SysRoleMapper.xml）。 */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
