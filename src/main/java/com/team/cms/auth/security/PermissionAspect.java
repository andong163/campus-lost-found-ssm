package com.team.cms.auth.security;

import com.team.cms.auth.AuthConstants;
import com.team.cms.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 接口权限切面：根容器切面，可按注解拦截 MVC 子容器中的 Controller 方法。
 * 先要求已登录（401），再要求持有声明权限（403）；管理员拥有全部权限。
 */
@Aspect
@Component
public class PermissionAspect {
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        CurrentUser user = CurrentUserContext.require();
        if (user.hasRole(AuthConstants.ROLE_ADMIN) || user.hasPermission(requirePermission.value())) {
            return joinPoint.proceed();
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "无权限访问");
    }
}
