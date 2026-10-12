package com.team.cms.system.service;

import com.team.cms.system.mapper.DatabaseProbeMapper;
import com.team.cms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {
    private final DatabaseProbeMapper probe;
    public SystemHealthService(DatabaseProbeMapper probe) { this.probe = probe; }
    // Borrow connections inside this try boundary; a transaction interceptor would fail before it.
    public void checkDatabase() {
        try {
            if (probe.ping() != 1 || probe.countRoles() < 4) {
                throw new IllegalStateException("schema not initialized");
            }
            probe.checkItemSchema();
            probe.checkClaimSchema();
        } catch (Exception error) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "数据库连接或初始化检查失败");
        }
    }
}
