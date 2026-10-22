package com.team.cms.audit.service;

import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 业务动作的审计包装：记录 SUCCESS/FAILURE、耗时、操作者快照和 requestId。
 *
 * <p>审计写入失败只记录一条不含敏感参数的告警，不改变业务结果，也不把已提交的事务重新抛成 500。
 * 因此前端不会因为“日志没写上”而重复执行一次已经成功的审核/认领动作。</p>
 */
@Component
public class AuditTemplate {
    private static final Logger LOG = LoggerFactory.getLogger(AuditTemplate.class);
    private static final String REQUEST_ID_MDC = "requestId";

    private final AuditWriter auditWriter;

    public AuditTemplate(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    public <T> T execute(String module, String action, Long targetId, Supplier<T> business) {
        long start = System.nanoTime();
        try {
            T result = business.get();
            safeWrite(module, action, targetId, "SUCCESS", elapsedMs(start));
            return result;
        } catch (RuntimeException error) {
            safeWrite(module, action, targetId, "FAILURE", elapsedMs(start));
            throw error;
        }
    }

    public void run(String module, String action, Long targetId, Runnable business) {
        execute(module, action, targetId, () -> {
            business.run();
            return null;
        });
    }

    private void safeWrite(String module, String action, Long targetId, String result, long elapsedMs) {
        try {
            CurrentUser user = CurrentUserContext.get();
            String requestId = MDC.get(REQUEST_ID_MDC);
            if (requestId == null || requestId.trim().isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }
            auditWriter.write(new AuditEntry(
                user == null ? null : user.getId(),
                user == null ? "" : user.getDisplayName(),
                module, action, targetId, requestId, result, elapsedMs, LocalDateTime.now()));
        } catch (RuntimeException auditFailure) {
            // 不记录异常 message/参数，避免把请求体中的联系方式或证据带进日志。
            LOG.warn("审计写入失败 module={} action={} type={}",
                module, action, auditFailure.getClass().getName());
        }
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
