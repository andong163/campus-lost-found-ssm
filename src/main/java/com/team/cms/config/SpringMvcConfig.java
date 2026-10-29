package com.team.cms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.web.AuthInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
// Controllers are child-container beans; the parent's AOP post-processors are not inherited.
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = "com.team.cms", useDefaultFilters = false, includeFilters = {
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
})
public class SpringMvcConfig implements WebMvcConfigurer {
    @Bean public LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    @Override public org.springframework.validation.Validator getValidator() { return validator(); }
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                // Do not silently truncate IDs such as 1.9 to 1 in a destructive batch request.
                mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
                mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
                ((MappingJackson2HttpMessageConverter) converter).setObjectMapper(mapper);
            }
        }
    }
    // 根容器声明的登录拦截器：保护 /api/**，白名单为注册、登录与系统健康接口。
    @Autowired private AuthInterceptor authInterceptor;

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(AuthConstants.PUBLIC_PATHS);
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Narrow mappings leave unknown /api routes with the unified JSON 404 contract.
        registry.addResourceHandler("/pages/**").addResourceLocations("/pages/");
        registry.addResourceHandler("/assets/**").addResourceLocations("/assets/");
    }
    // No wildcard credentialed CORS. The initial WAR serves same-origin API requests.
}
