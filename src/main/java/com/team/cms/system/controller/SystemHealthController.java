package com.team.cms.system.controller;

import com.team.cms.common.api.Result;
import com.team.cms.system.service.SystemHealthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system")
public class SystemHealthController {
    private final SystemHealthService service;
    public SystemHealthController(SystemHealthService service) { this.service = service; }
    @GetMapping("/health") public Result<Map<String, String>> health() {
        return Result.ok(Map.of("application", "campus-lost-found", "status", "UP"));
    }
    @GetMapping("/readiness") public Result<Map<String, String>> readiness() {
        service.checkDatabase();
        return Result.ok(Map.of("database", "UP", "roleSeed", "UP", "itemSchema", "UP", "claimSchema", "UP"));
    }
}
