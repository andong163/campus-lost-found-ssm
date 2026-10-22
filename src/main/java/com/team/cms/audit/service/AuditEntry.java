package com.team.cms.audit.service;

import java.time.LocalDateTime;

/** 一条结构化审计记录；由调用方填好安全字段，AuditService 负责落库。 */
public final class AuditEntry {
    private final Long operatorId;
    private final String operatorName;
    private final String module;
    private final String action;
    private final Long targetId;
    private final String requestId;
    private final String result;
    private final long elapsedMs;
    private final LocalDateTime createdAt;

    public AuditEntry(Long operatorId, String operatorName, String module, String action,
            Long targetId, String requestId, String result, long elapsedMs, LocalDateTime createdAt) {
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.module = module;
        this.action = action;
        this.targetId = targetId;
        this.requestId = requestId;
        this.result = result;
        this.elapsedMs = elapsedMs;
        this.createdAt = createdAt;
    }

    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getRequestId() { return requestId; }
    public String getResult() { return result; }
    public long getElapsedMs() { return elapsedMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
