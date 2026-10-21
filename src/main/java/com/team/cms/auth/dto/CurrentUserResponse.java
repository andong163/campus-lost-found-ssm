package com.team.cms.auth.dto;

import com.team.cms.auth.security.CurrentUser;
import java.util.LinkedHashSet;
import java.util.Set;

/** 当前用户视图：不含密码哈希，只暴露 id/用户名/昵称/角色/可用权限。 */
public class CurrentUserResponse {
    private final Long id;
    private final String username;
    private final String displayName;
    private final Set<String> roles;
    private final Set<String> permissions;

    public CurrentUserResponse(Long id, String username, String displayName,
            Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.roles = roles;
        this.permissions = permissions;
    }

    public static CurrentUserResponse from(CurrentUser user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
            new LinkedHashSet<>(user.getRoles()), new LinkedHashSet<>(user.getPermissions()));
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }
}
