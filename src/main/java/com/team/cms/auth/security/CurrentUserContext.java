package com.team.cms.auth.security;

import com.team.cms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 当前请求用户上下文：由登录拦截器在请求入口写入，请求结束时清除。
 * 权限切面与业务服务均可通过 {@link #get()} / {@link #require()} 读取同一份身份。
 */
public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() { }

    public static void set(CurrentUser user) { HOLDER.set(user); }
    public static CurrentUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    /** 未登录或会话失效时返回 401。 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        return user;
    }
}
