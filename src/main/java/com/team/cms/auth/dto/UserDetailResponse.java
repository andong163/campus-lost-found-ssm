package com.team.cms.auth.dto;

import com.team.cms.auth.entity.SysUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 用户详情：角色与由角色折算出的可用权限，同样不包含密码哈希。 */
public class UserDetailResponse {
    private final Long id;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final List<String> roles;
    private final List<String> permissions;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserDetailResponse(Long id, String username, String displayName, boolean enabled,
            Collection<String> roles, Collection<String> permissions,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.enabled = enabled;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserDetailResponse of(SysUser user, Collection<String> roles,
            Collection<String> permissions) {
        return new UserDetailResponse(user.getId(), user.getUsername(), user.getDisplayName(),
            user.getEnabled() != null && user.getEnabled() == 1, roles, permissions,
            user.getCreatedAt(), user.getUpdatedAt());
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public List<String> getRoles() { return roles; }
    public List<String> getPermissions() { return permissions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
