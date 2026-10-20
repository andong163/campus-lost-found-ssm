package com.team.cms;

import com.team.cms.auth.service.AdminInitializer;
import com.team.cms.config.SpringConfig;
import com.team.cms.config.SpringMvcConfig;
import javax.sql.DataSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.*;

/** 郭金滔 GJT R1 一次性管理员初始化验收测试。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:admin;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password=",
    "cms.admin.username=admin", "cms.admin.password=admin12345"})
@Sql("/auth-schema-test.sql")
public class AdminInitializerTest {
    @Autowired private AdminInitializer initializer;
    @Autowired private DataSource dataSource;

    @Test public void createsAdminWithAdminRoleAndHashedPassword() {
        // 容器刷新事件发生在 @Sql 建表之前（软失败），此处手动再次触发以验证真实逻辑。
        initializer.initialize();
        JdbcTemplate sql = new JdbcTemplate(dataSource);

        Integer count = sql.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = 'admin'", Integer.class);
        assertEquals(1, count.intValue());

        Integer roleCount = sql.queryForObject(
            "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id WHERE u.username = 'admin' AND r.code = 'ADMIN'", Integer.class);
        assertEquals(1, roleCount.intValue());

        String stored = sql.queryForObject("SELECT password_hash FROM sys_user WHERE username = 'admin'", String.class);
        assertFalse("口令不应以明文入库", stored.contains("admin12345"));
    }
}
