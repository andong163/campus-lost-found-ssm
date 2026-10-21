package com.team.cms.auth.web;

import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.common.exception.BusinessException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：从会话读取当前用户写入请求上下文；未登录（无会话或会话无身份）返回 401。
 * 由 SpringMvcConfig 声明为 Bean 并注册，白名单见 AuthConstants.PUBLIC_PATHS。
 */
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        CurrentUser user = null;
        if (session != null) {
            Object attribute = session.getAttribute(AuthConstants.SESSION_USER);
            if (attribute instanceof CurrentUser) {
                user = (CurrentUser) attribute;
            }
        }
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        CurrentUserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
