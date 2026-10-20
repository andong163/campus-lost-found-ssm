package com.team.cms.auth.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的权限点（如 "user:manage"）。
 * 由 {@link PermissionAspect} 在方法调用前校验；未登录返回 401，已登录但缺少权限返回 403。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 权限代码，形如 "user:manage"。 */
    String value();
}
