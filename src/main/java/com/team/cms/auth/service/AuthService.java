package com.team.cms.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.dto.CurrentUserResponse;
import com.team.cms.auth.dto.LoginRequest;
import com.team.cms.auth.dto.RegisterRequest;
import com.team.cms.auth.entity.SysRole;
import com.team.cms.auth.entity.SysUser;
import com.team.cms.auth.mapper.SysPermissionMapper;
import com.team.cms.auth.mapper.SysRoleMapper;
import com.team.cms.auth.mapper.SysUserMapper;
import com.team.cms.auth.mapper.SysUserRoleMapper;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.PasswordHasher;
import com.team.cms.common.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 身份服务：注册、登录、当前用户装载、管理员初始化。 */
@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordHasher passwordHasher;

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper, SysUserRoleMapper userRoleMapper,
            PasswordHasher passwordHasher) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordHasher = passwordHasher;
    }

    /** 注册：密码带盐哈希后落库，一律分配 EDITOR 学生角色，忽略请求中的角色/权限字段。 */
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse register(RegisterRequest request) {
        if (findByUsername(request.getUsername()) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(passwordHasher.hash(request.getPassword()));
        user.setEnabled(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }
        assignRole(user.getId(), AuthConstants.ROLE_EDITOR);
        return CurrentUserResponse.from(loadCurrentUser(user.getId()));
    }

    /** 登录：查无此人、密码错误、账号停用统一返回 401，不泄露具体原因。 */
    public CurrentUser login(LoginRequest request) {
        SysUser user = findByUsername(request.getUsername());
        if (user == null
                || !passwordHasher.matches(request.getPassword(), user.getPasswordHash())
                || (user.getEnabled() != null && user.getEnabled() == 0)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return loadCurrentUser(user.getId());
    }

    /** 从数据库装载当前用户的身份快照（角色 + 权限）。 */
    public CurrentUser loadCurrentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = permissionMapper.selectPermissionCodesByUserId(userId);
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(),
            new LinkedHashSet<>(roles), new LinkedHashSet<>(permissions));
    }

    /** 确保管理员存在：已存在则跳过，否则创建并分配 ADMIN 角色。 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureAdmin(String username, String password) {
        if (findByUsername(username) != null) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName("管理员");
        user.setPasswordHash(passwordHasher.hash(password));
        user.setEnabled(1);
        userMapper.insert(user);
        assignRole(user.getId(), AuthConstants.ROLE_ADMIN);
    }

    private void assignRole(Long userId, String roleCode) {
        SysRole role = roleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode));
        if (role == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "角色初始化缺失：" + roleCode);
        }
        userRoleMapper.insert(userId, role.getId());
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }
}
