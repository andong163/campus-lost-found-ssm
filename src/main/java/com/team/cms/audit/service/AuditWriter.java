package com.team.cms.audit.service;

/**
 * 审计写入端口。AuditTemplate 只依赖该接口，测试可注入一个必定失败的实现，
 * 验证“审计失败不影响已成功的业务响应”。
 */
@FunctionalInterface
public interface AuditWriter {
    void write(AuditEntry entry);
}
