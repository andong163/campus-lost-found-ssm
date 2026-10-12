package com.team.cms.common.web;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.*;
import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Request diagnostics only. Persisted business audit logs belong to 杨程's module. */
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLogFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        String previousRequestId = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try { chain.doFilter(request, response); }
        finally {
            LOG.info("{} {} status={} elapsedMs={}", request.getMethod(),
                request.getRequestURI().replaceAll("[\\r\\n]", ""), response.getStatus(),
                (System.nanoTime() - start) / 1_000_000);
            if (previousRequestId == null) MDC.remove("requestId");
            else MDC.put("requestId", previousRequestId);
        }
    }
}
