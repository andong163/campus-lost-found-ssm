package com.team.cms.audit.dto;

import com.team.cms.audit.entity.OperationLog;
import java.time.LocalDateTime;

/** 日志查询响应；只包含审计快照字段，不包含业务正文或私密数据。 */
public final class OperationLogView {
    private final Long id;
    private final Long operatorId;
    private final String operatorName;
    private final String module;
    private final String action;
    private final Long targetId;
    private final String requestId;
    private final String result;
    private final Long elapsedMs;
    private final LocalDateTime createdAt;

    public OperationLogView(Long id, Long operatorId, String operatorName, String module, String action,
            Long targetId, String requestId, String result, Long elapsedMs, LocalDateTime createdAt) {
        this.id = id;
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

    public static OperationLogView from(OperationLog log) {
        return new OperationLogView(log.getId(), log.getOperatorId(), log.getOperatorName(),
            log.getModule(), log.getAction(), log.getTargetId(), log.getRequestId(),
            log.getResult(), log.getElapsedMs(), log.getCreatedAt());
    }

    public Long getId() { return id; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getRequestId() { return requestId; }
    public String getResult() { return result; }
    public Long getElapsedMs() { return elapsedMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
