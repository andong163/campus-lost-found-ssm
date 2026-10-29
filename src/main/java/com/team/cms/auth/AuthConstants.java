package com.team.cms.auth;

/** 身份模块共享常量。 */
public final class AuthConstants {
    private AuthConstants() { }

    /**
     * 会话中保存的当前用户快照。
     * <p>登录时写入，之后每次请求都由 {@link com.team.cms.auth.web.AuthInterceptor}
     * 用其中的用户主键从数据库重新装载并覆盖，因此会话里不会残留旧角色或旧权限。</p>
     */
    public static final String SESSION_USER = "CMS_CURRENT_USER";
    /** 登录会话中的写请求防护令牌。 */
    public static final String SESSION_CSRF_TOKEN = "CMS_CSRF_TOKEN";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_REVIEWER = "REVIEWER";
    public static final String ROLE_VIEWER = "VIEWER";

    /** 无需登录即可访问的接口路径（ant 风格，供登录拦截器白名单使用）。 */
    public static final String[] PUBLIC_PATHS = {
        "/api/auth/register",
        "/api/auth/login",
        "/api/public/categories",
        "/api/system/**"
    };
}
