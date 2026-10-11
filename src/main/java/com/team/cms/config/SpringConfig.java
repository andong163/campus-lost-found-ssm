package com.team.cms.config;

import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Import(PersistenceConfig.class)
@ComponentScan(basePackages = "com.team.cms", excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class),
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class)
})
public class SpringConfig { }
