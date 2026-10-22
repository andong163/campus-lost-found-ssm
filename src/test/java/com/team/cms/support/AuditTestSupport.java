package com.team.cms.support;

import com.team.cms.audit.service.AuditService;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.config.SpringConfig;
import org.junit.After;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** YC R1 审计测试基类：只启动根容器和审计所需的最小依赖。 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
@TestPropertySource(properties = {
    "jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:audit;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa",
    "jdbc.password="
})
@Sql(scripts = "/review-claim-schema.sql", config = @SqlConfig(encoding = "UTF-8"))
public abstract class AuditTestSupport {

    @Autowired protected AuditService auditService;

    @After
    public void clearAuditTestState() {
        org.slf4j.MDC.clear();
    }

    protected static void assertStatus(HttpStatus expected, Runnable action) {
        try {
            action.run();
            fail("应抛出 " + expected.value() + " 业务异常");
        } catch (BusinessException error) {
            assertEquals("错误码不符: " + error.getMessage(), expected, error.getStatus());
        }
    }
}
