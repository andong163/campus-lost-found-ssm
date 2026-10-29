package com.team.cms.auth.dto;

import com.team.cms.auth.entity.SysUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 用户列表项：不包含密码哈希等敏感字段。 */
public class UserSummaryResponse {
    private final Long id;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final List<String> roles;
    private final LocalDateTime createdAt;

    public UserSummaryResponse(Long id, String username, String displayName, boolean enabled,
            Collection<String> roles, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.enabled = enabled;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.createdAt = createdAt;
    }

    public static UserSummaryResponse of(SysUser user, Collection<String> roles) {
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(),
            user.getEnabled() != null && user.getEnabled() == 1, roles, user.getCreatedAt());
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public List<String> getRoles() { return roles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
