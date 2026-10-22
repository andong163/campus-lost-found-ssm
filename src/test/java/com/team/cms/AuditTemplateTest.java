package com.team.cms;

import com.team.cms.audit.service.AuditEntry;
import com.team.cms.audit.service.AuditTemplate;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 审计包装的独立单元测试：成功/失败记录、requestId 关联、审计失败不改变业务结果。 */
public class AuditTemplateTest {

    private final List<AuditEntry> entries = new ArrayList<>();

    @After
    public void clean() {
        CurrentUserContext.clear();
        MDC.clear();
    }

    @Test
    public void successRecordsOperatorActionTargetAndRequestId() {
        CurrentUserContext.set(new CurrentUser(7L, "reviewer7", "审核员七",
            Collections.singleton("REVIEWER"), Collections.singleton("review:handle")));
        MDC.put("requestId", "req-abc");
        AuditTemplate template = new AuditTemplate(entries::add);

        String result = template.execute("review", "REVIEW_DECIDE", 42L, () -> "done");

        assertEquals("done", result);
        assertEquals(1, entries.size());
        AuditEntry entry = entries.get(0);
        assertEquals(Long.valueOf(7L), entry.getOperatorId());
        assertEquals("审核员七", entry.getOperatorName());
        assertEquals("review", entry.getModule());
        assertEquals("REVIEW_DECIDE", entry.getAction());
        assertEquals(Long.valueOf(42L), entry.getTargetId());
        assertEquals("req-abc", entry.getRequestId());
        assertEquals("SUCCESS", entry.getResult());
        assertTrue(entry.getElapsedMs() >= 0);
    }

    @Test
    public void businessFailureIsRecordedThenRethrown() {
        AuditTemplate template = new AuditTemplate(entries::add);

        try {
            template.run("claim", "CLAIM_DECIDE", 9L, () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }

        assertEquals(1, entries.size());
        assertEquals("FAILURE", entries.get(0).getResult());
    }

    @Test
    public void auditWriteFailureDoesNotTurnSuccessIntoSideEffect() {
        AuditTemplate template = new AuditTemplate(entry -> {
            throw new IllegalStateException("audit database down");
        });

        String result = template.execute("claim", "CLAIM_APPLY", 11L, () -> "business-ok");

        assertEquals("business-ok", result);
    }

    @Test
    public void missingRequestIdFallsBackToGeneratedIdAndAnonymousOperatorIsNull() {
        AuditTemplate template = new AuditTemplate(entries::add);

        template.run("claim", "CLAIM_CANCEL", null, () -> { });

        AuditEntry entry = entries.get(0);
        assertEquals(null, entry.getOperatorId());
        assertEquals("", entry.getOperatorName());
        assertFalse(entry.getRequestId().isEmpty());
        assertEquals(36, entry.getRequestId().length());
        assertEquals(null, entry.getTargetId());
    }
}
