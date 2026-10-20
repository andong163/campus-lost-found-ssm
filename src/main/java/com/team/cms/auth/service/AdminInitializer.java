package com.team.cms.auth.service;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 本机一次性管理员初始化：从 JVM 系统属性 cms.admin.username / cms.admin.password，
 * 或环境变量 CMS_ADMIN_USERNAME / CMS_ADMIN_PASSWORD 读取口令，明文绝不进入仓库。
 * 未配置口令时静默跳过（不触碰数据库），避免破坏无口令的测试/本地环境。
 * ensureAdmin 幂等，因此本方法可安全重复调用（含容器多次刷新事件）。
 */
@Component
public class AdminInitializer {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{4,30}$");

    private final Environment environment;
    private final AuthService authService;

    public AdminInitializer(Environment environment, AuthService authService) {
        this.environment = environment;
        this.authService = authService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initialize() {
        String password = firstNonBlank(
            environment.getProperty("cms.admin.password"),
            System.getenv("CMS_ADMIN_PASSWORD"));
        if (password == null) {
            log.info("未配置 CMS_ADMIN_PASSWORD，跳过一次性的管理员初始化");
            return;
        }
        String username = firstNonBlank(
            environment.getProperty("cms.admin.username"),
            System.getenv("CMS_ADMIN_USERNAME"),
            "admin");
        if (password.length() < 8 || password.length() > 64) {
            throw new IllegalStateException("管理员口令需为 8~64 个字符");
        }
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalStateException("管理员用户名需为 4~30 位字母、数字或下划线");
        }
        try {
            authService.ensureAdmin(username, password);
            log.info("管理员账号 {} 已就绪", username);
        } catch (Exception error) {
            log.error("管理员初始化失败（不影响应用启动），请检查数据库或口令配置", error);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
