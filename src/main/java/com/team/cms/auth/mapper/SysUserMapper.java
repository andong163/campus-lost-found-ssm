package com.team.cms.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.team.cms.auth.dto.UserQuery;
import com.team.cms.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    /** 分页查询用户，条件见 SysUserMapper.xml；由分页插件自动追加 LIMIT。 */
    IPage<SysUser> selectUserPage(IPage<SysUser> page, @Param("q") UserQuery query);

    /** 当前仍可登录的管理员账号数，用于「最后一个有效管理员」保护。 */
    @Select("SELECT COUNT(*) FROM sys_user u"
        + " JOIN sys_user_role ur ON ur.user_id = u.id"
        + " JOIN sys_role r ON r.id = ur.role_id"
        + " WHERE r.code = 'ADMIN' AND r.deleted = 0 AND u.deleted = 0 AND u.enabled = 1")
    int countEnabledAdmins();
}
