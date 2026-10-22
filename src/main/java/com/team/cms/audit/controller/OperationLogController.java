package com.team.cms.audit.controller;

import com.team.cms.audit.dto.OperationLogQuery;
import com.team.cms.audit.dto.OperationLogView;
import com.team.cms.audit.service.AuditService;
import com.team.cms.auth.security.RequirePermission;
import com.team.cms.common.api.PageResult;
import com.team.cms.common.api.Result;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员/只读审计入口：分页查询业务操作日志，普通学生无权访问。 */
@RestController
@RequestMapping("/api")
public class OperationLogController {
    private static final String AUDIT_VIEW = "audit:view";

    private final AuditService auditService;

    public OperationLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/operation-logs")
    @RequirePermission(AUDIT_VIEW)
    public Result<PageResult<OperationLogView>> page(@Valid @ModelAttribute OperationLogQuery query) {
        return Result.ok(auditService.query(query));
    }
}
