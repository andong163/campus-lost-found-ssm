package com.team.cms.config;

import com.team.cms.common.web.RequestLogFilter;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/** Servlet 3 startup: parent owns services, child owns MVC. No web.xml needed. */
public class WebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override protected Class<?>[] getRootConfigClasses() { return new Class<?>[]{SpringConfig.class}; }
    @Override protected Class<?>[] getServletConfigClasses() { return new Class<?>[]{SpringMvcConfig.class}; }
    @Override protected String[] getServletMappings() { return new String[]{"/"}; }
    @Override protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        registration.setInitParameter("throwExceptionIfNoHandlerFound", "true");
    }
    @Override protected Filter[] getServletFilters() {
        return new Filter[]{new CharacterEncodingFilter("UTF-8", true), new RequestLogFilter()};
    }
    @Override public void onStartup(ServletContext servletContext) throws ServletException {
        super.onStartup(servletContext);
        // 会话 30 分钟无操作失效。
        servletContext.setSessionTimeout(30);
    }
}
