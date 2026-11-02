package com.team.cms.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.dto.CreateRoleRequest;
import com.team.cms.auth.dto.PermissionResponse;
import com.team.cms.auth.dto.RolePermissionRow;
import com.team.cms.auth.dto.RoleResponse;
import com.team.cms.auth.dto.UpdateRoleRequest;
import com.team.cms.auth.entity.SysPermission;
import com.team.cms.auth.entity.SysRole;
import com.team.cms.auth.mapper.SysPermissionMapper;
import com.team.cms.auth.mapper.SysRoleMapper;
import com.team.cms.auth.mapper.SysRolePermissionMapper;
import com.team.cms.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色与权限维护：角色增改、角色权限替换、权限种子清单。 */
@Service
public class RoleAdminService {
    private static final String PERM_USER_MANAGE = "user:manage";
    private static final String PERM_ROLE_MANAGE = "role:manage";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public RoleAdminService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
            SysRolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /** 全部角色及其已授予权限。 */
    public List<RoleResponse> list() {
        List<SysRole> roles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        Map<Long, List<String>> permissions = loadPermissionsByRole();
        List<RoleResponse> responses = new ArrayList<>();
        for (SysRole role : roles) {
            responses.add(RoleResponse.of(role,
                permissions.getOrDefault(role.getId(), Collections.emptyList())));
        }
        return responses;
    }

    public RoleResponse detail(Long id) {
        SysRole role = requireRole(id);
        return RoleResponse.of(role,
            loadPermissionsByRole().getOrDefault(id, Collections.emptyList()));
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleResponse create(CreateRoleRequest request) {
        String code = request.getCode().trim();
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code)) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "角色代码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(request.getName().trim());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(HttpStatus.CONFLICT, "角色代码已存在");
        }
        return RoleResponse.of(role, Collections.emptyList());
    }

    /** 角色代码不可变，只允许修改展示名称。 */
    @Transactional(rollbackFor = Exception.class)
    public RoleResponse update(Long id, UpdateRoleRequest request) {
        SysRole role = requireRole(id);
        role.setName(request.getName().trim());
        roleMapper.updateById(role);
        return detail(id);
    }

    /** 替换式分配权限；管理员角色必须保留用户管理与角色管理权限，避免把自己锁在门外。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<String> permissionCodes) {
        SysRole role = requireRole(id);
        List<String> codes = normalize(permissionCodes);
        if (codes.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "权限列表不能为空");
        }
        List<SysPermission> permissions = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>().in(SysPermission::getCode, codes));
        if (permissions.size() != codes.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "存在无效的权限代码");
        }
        if (AuthConstants.ROLE_ADMIN.equals(role.getCode())
                && (!codes.contains(PERM_USER_MANAGE) || !codes.contains(PERM_ROLE_MANAGE))) {
            throw new BusinessException(HttpStatus.CONFLICT, "管理员角色必须保留用户管理与角色管理权限");
        }
        rolePermissionMapper.deleteByRoleId(id);
        for (SysPermission permission : permissions) {
            rolePermissionMapper.insert(id, permission.getId());
        }
    }

    /** 权限种子清单，供前端分配角色权限时选择。 */
    public List<PermissionResponse> permissions() {
        List<SysPermission> permissions = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getCode));
        List<PermissionResponse> responses = new ArrayList<>();
        for (SysPermission permission : permissions) {
            responses.add(PermissionResponse.of(permission));
        }
        return responses;
    }

    private Map<Long, List<String>> loadPermissionsByRole() {
        Map<Long, List<String>> byRole = new LinkedHashMap<>();
        for (RolePermissionRow row : rolePermissionMapper.selectAllRolePermissionCodes()) {
            byRole.computeIfAbsent(row.getRoleId(), key -> new ArrayList<>()).add(row.getPermissionCode());
        }
        return byRole;
    }

    private SysRole requireRole(Long id) {
        SysRole role = id == null ? null : roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        return role;
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
