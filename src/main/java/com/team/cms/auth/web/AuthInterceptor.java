package com.team.cms.auth.web;

import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.auth.service.AuthService;
import com.team.cms.common.exception.BusinessException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：会话只作为登录凭据入口，不给权限做缓存。
 * <p>每次请求都按会话中的用户主键从数据库重新装载角色与权限，再把最新的身份写回会话快照并放入
 * 请求上下文。因此停用账号、调整角色或撤销权限会对既有会话立即生效；账号被停用或删除后，
 * 下一次请求即返回 401。白名单见 {@link AuthConstants#PUBLIC_PATHS}。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Long userId = userIdOf(session == null ? null : session.getAttribute(AuthConstants.SESSION_USER));
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        CurrentUser user = authService.loadActiveUser(userId);
        if (user == null) {
            invalidateQuietly(session);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        // 覆盖会话快照：保持属性类型与既有调用方约定一致，同时让旧权限立即失效。
        session.setAttribute(AuthConstants.SESSION_USER, user);
        CurrentUserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    /** 兼容两种取值：当前用户对象，或直接是用户主键。 */
    private Long userIdOf(Object attribute) {
        if (attribute instanceof CurrentUser) {
            return ((CurrentUser) attribute).getId();
        }
        if (attribute instanceof Number) {
            return ((Number) attribute).longValue();
        }
        return null;
    }

    private static void invalidateQuietly(HttpSession session) {
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // 会话已被销毁，无需重复处理。
        }
    }
}
