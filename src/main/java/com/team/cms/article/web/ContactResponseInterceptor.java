package com.team.cms.article.web;

import com.team.cms.audit.service.AuditEntry;
import com.team.cms.audit.service.AuditWriter;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.servlet.http.*;
import org.springframework.web.servlet.HandlerInterceptor;

/** 先于认证执行，让成功、拒绝与未登录响应均禁止缓存。 */
public final class ContactResponseInterceptor implements HandlerInterceptor {
    private final AuditWriter audit;
    public ContactResponseInterceptor(AuditWriter audit) { this.audit=audit; }
    @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler) {
        res.setHeader("Cache-Control","no-store, private, max-age=0");
        res.setHeader("Pragma","no-cache"); res.setDateHeader("Expires",0);
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception ex) {
        if (res.getStatus()!=401 || !(req.getRequestURI().endsWith("/contact") || req.getRequestURI().endsWith("/found-contact"))) return;
        String[] parts=req.getRequestURI().split("/");
        Long id=null;
        try { id=Long.valueOf(parts[parts.length-2]); } catch (NumberFormatException ignored) { }
        try {
            audit.write(new AuditEntry(null,"","article","CONTACT_READ",id,UUID.randomUUID().toString(),
                "FAILURE",0,LocalDateTime.now()));
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("匿名联系读取审计失败 type={}",error.getClass().getName());
        }
    }
}
