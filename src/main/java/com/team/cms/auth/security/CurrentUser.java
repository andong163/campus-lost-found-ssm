package com.team.cms.auth.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录成功后从数据库装载、并缓存进会话的当前用户身份快照。
 * 全组通过 CurrentUserContext 获取同一套身份，通过 {@link #hasPermission(String)} 判断权限。
 */
public final class CurrentUser {
    private final Long id;
    private final String username;
    private final String displayName;
    private final Set<String> roles;
    private final Set<String> permissions;

    public CurrentUser(Long id, String username, String displayName, Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }

    public boolean hasRole(String roleCode) { return roles.contains(roleCode); }
    public boolean hasPermission(String permissionCode) { return permissions.contains(permissionCode); }
}
