package com.team.cms.support;

import com.team.cms.auth.security.RequirePermission;
import com.team.cms.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 测试探针：声明需要 user:manage 权限的接口，用于验证权限切面。 */
@RestController
@RequestMapping("/api/auth-probe")
public class AuthProbeController {
    @GetMapping("/admin-only")
    @RequirePermission("user:manage")
    public Result<String> adminOnly() {
        return Result.ok("granted");
    }
}
