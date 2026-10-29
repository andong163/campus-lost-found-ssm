package com.team.cms.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.dto.UserDetailResponse;
import com.team.cms.auth.dto.UserQuery;
import com.team.cms.auth.dto.UserRoleRow;
import com.team.cms.auth.dto.UserSummaryResponse;
import com.team.cms.auth.entity.SysRole;
import com.team.cms.auth.entity.SysUser;
import com.team.cms.auth.mapper.SysPermissionMapper;
import com.team.cms.auth.mapper.SysRoleMapper;
import com.team.cms.auth.mapper.SysUserMapper;
import com.team.cms.auth.mapper.SysUserRoleMapper;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户管理：分页查询、启停、角色分配，并守住「最后一个有效管理员」这条底线。 */
@Service
public class UserAdminService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public UserAdminService(SysUserMapper userMapper, SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper, SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** 分页查询用户；角色一次性批量装载，响应中不含密码哈希。 */
    public PageResult<UserSummaryResponse> page(UserQuery query) {
        Page<SysUser> request = new Page<>(query.getPage(), query.getSize());
        IPage<SysUser> result = userMapper.selectUserPage(request, query);
        List<Long> userIds = new ArrayList<>();
        for (SysUser user : result.getRecords()) {
            userIds.add(user.getId());
        }
        Map<Long, List<String>> rolesByUser = loadRoles(userIds);
        List<UserSummaryResponse> records = new ArrayList<>();
        for (SysUser user : result.getRecords()) {
            records.add(UserSummaryResponse.of(user,
                rolesByUser.getOrDefault(user.getId(), Collections.emptyList())));
        }
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    public UserDetailResponse detail(Long id) {
        SysUser user = requireUser(id);
        return UserDetailResponse.of(user, roleMapper.selectRoleCodesByUserId(id),
            permissionMapper.selectPermissionCodesByUserId(id));
    }

    /** 启停账号：停用时禁止让系统失去最后一个有效管理员。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, boolean enabled) {
        SysUser user = requireUser(id);
        if (!enabled) {
            assertNotLastAdmin(user);
        }
        user.setEnabled(enabled ? 1 : 0);
        userMapper.updateById(user);
    }

    /** 替换式分配角色：提交的集合即最终角色；移除 ADMIN 时同样保护最后一个管理员。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<String> roleCodes) {
        SysUser user = requireUser(id);
        List<String> codes = normalize(roleCodes);
        if (codes.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色列表不能为空");
        }
        List<SysRole> roles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, codes));
        if (roles.size() != codes.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "存在无效的角色代码");
        }
        List<String> current = roleMapper.selectRoleCodesByUserId(id);
        if (current.contains(AuthConstants.ROLE_ADMIN) && !codes.contains(AuthConstants.ROLE_ADMIN)) {
            assertNotLastAdmin(user);
        }
        userRoleMapper.deleteByUserId(id);
        for (SysRole role : roles) {
            userRoleMapper.insert(id, role.getId());
        }
    }

    private void assertNotLastAdmin(SysUser user) {
        if (user.getEnabled() == null || user.getEnabled() == 0) {
            return;
        }
        if (!roleMapper.selectRoleCodesByUserId(user.getId()).contains(AuthConstants.ROLE_ADMIN)) {
            return;
        }
        if (userMapper.countEnabledAdmins() <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "不能停用或移除最后一个有效管理员");
        }
    }

    private Map<Long, List<String>> loadRoles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<String>> rolesByUser = new LinkedHashMap<>();
        for (UserRoleRow row : roleMapper.selectRoleCodesByUserIds(userIds)) {
            rolesByUser.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row.getRoleCode());
        }
        return rolesByUser;
    }

    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private static List<String> normalize(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }
}
