package com.team.cms;

import com.team.cms.audit.entity.OperationLog;
import com.team.cms.common.web.RequestLogFilter;
import com.team.cms.support.ReviewTestSupport;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.Assert.*;

/** 真实过滤器关联号必须传递到事务内的工作流操作记录。 */
public class WorkflowRequestAuditTest extends ReviewTestSupport {
    @Test public void publishOfflineAndDeleteKeepRequestIdentity() throws Exception {
        asAdmin();
        check("OFFLINE", () -> itemWorkflowService.offline(2L, article(2L).getVersion(), null));
        check("PUBLISH", () -> itemWorkflowService.publish(2L, article(2L).getVersion()));
        check("DELETE", () -> itemWorkflowService.delete(2L, article(2L).getVersion()));
        assertEquals(3, operationLogMapper.selectList(null).size());
    }

    private void check(String action, Runnable business) throws Exception {
        MDC.put("requestId", "outer-context");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/items/2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestLogFilter().doFilter(request, response, (req, res) -> business.run());
        OperationLog log = operationLogMapper.selectList(null).stream()
            .filter(entry -> action.equals(entry.getAction())).findFirst().orElseThrow();
        assertEquals(response.getHeader("X-Request-Id"), log.getRequestId());
        assertNotEquals("outer-context", log.getRequestId());
        assertEquals("outer-context", MDC.get("requestId"));
        assertTrue(log.getElapsedMs() >= 0);
    }

    @Test public void nonHttpCallGetsAnIndependentCorrelationId() {
        asAdmin(); MDC.clear();
        itemWorkflowService.offline(2L, article(2L).getVersion(), null);
        String requestId = operationLogMapper.selectList(null).get(0).getRequestId();
        assertEquals(requestId, UUID.fromString(requestId).toString());
        assertNull(MDC.get("requestId"));
    }
}
