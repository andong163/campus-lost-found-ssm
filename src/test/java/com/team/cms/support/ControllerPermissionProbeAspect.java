package com.team.cms.support;

import com.team.cms.common.exception.BusinessException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Test-only root aspect that must intercept a controller in the MVC child container. */
@Aspect
@Component
public class ControllerPermissionProbeAspect {
    @Before("execution(* com.team.cms.support.WebProbeController.deniedByRootAspect(..))")
    public void deny() {
        throw new BusinessException(HttpStatus.FORBIDDEN, "测试权限拒绝");
    }
}
