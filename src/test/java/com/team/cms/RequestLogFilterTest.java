package com.team.cms;

import com.team.cms.common.web.RequestLogFilter;
import javax.servlet.ServletException;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.Assert.*;

public class RequestLogFilterTest {
    @Test public void restoresOuterDiagnosticContextEvenWhenTheChainThrows() throws Exception {
        String previous = MDC.get("requestId");
        MDC.put("requestId", "outer-request");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            try {
                new RequestLogFilter().doFilter(new MockHttpServletRequest("GET", "/probe"), response,
                    (request, reply) -> { throw new ServletException("expected failure"); });
                fail("Expected filter chain exception");
            } catch (ServletException expected) {
                assertEquals("outer-request", MDC.get("requestId"));
                assertNotEquals("outer-request", response.getHeader("X-Request-Id"));
            }
        } finally {
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
