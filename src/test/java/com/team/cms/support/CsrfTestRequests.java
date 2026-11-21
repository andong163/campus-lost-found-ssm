package com.team.cms.support;

import com.team.cms.auth.AuthConstants;
import javax.servlet.http.HttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 沿用真实登录生成的令牌，为已有成功写请求提供同会话请求头。 */
public final class CsrfTestRequests {
    private CsrfTestRequests() { }

    public static RequestPostProcessor csrf() {
        return request -> {
            HttpSession session = request.getSession(false);
            Object token = session == null ? null : session.getAttribute(AuthConstants.SESSION_CSRF_TOKEN);
            if (token instanceof String) request.addHeader(AuthConstants.CSRF_HEADER, token);
            return request;
        };
    }
}
