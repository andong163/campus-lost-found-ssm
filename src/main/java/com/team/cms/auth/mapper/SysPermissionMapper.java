package com.team.cms.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.auth.entity.SysPermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
    /** 查询指定用户经由其角色获得的全部权限代码（见 SysPermissionMapper.xml）。 */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
