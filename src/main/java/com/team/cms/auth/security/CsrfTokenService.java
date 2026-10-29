package com.team.cms.auth.security;

import com.team.cms.auth.AuthConstants;
import com.team.cms.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 为登录会话签发令牌，并校验物品草稿写请求携带的令牌。 */
@Component
public class CsrfTokenService {
    private final SecureRandom random = new SecureRandom();

    /** 每次登录换发新令牌，旧会话销毁后旧令牌不再有效。 */
    public String issue(HttpSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(AuthConstants.SESSION_CSRF_TOKEN, token);
        return token;
    }

    public String current(HttpSession session) {
        Object token = session == null ? null : session.getAttribute(AuthConstants.SESSION_CSRF_TOKEN);
        if (!(token instanceof String)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "写请求令牌无效");
        }
        return (String) token;
    }

    /** 同一会话令牌通过请求头提交；缺失或错误时拒绝写入。 */
    public void requireValid(HttpServletRequest request) {
        String expected = current(request.getSession(false));
        String supplied = request.getHeader(AuthConstants.CSRF_HEADER);
        if (supplied == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "写请求令牌无效");
        }
    }
}
