package com.team.cms.support;

import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.audit.mapper.OperationLogMapper;
import com.team.cms.audit.service.AuditService;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.config.SpringConfig;
import com.team.cms.review.ReviewPermissions;
import com.team.cms.review.mapper.ReviewRecordMapper;
import com.team.cms.review.service.ItemWorkflowService;
import com.team.cms.review.service.ReviewService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.After;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 第二轮审核工作流测试基类：只启动根容器，不依赖第三轮认领服务。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
@TestPropertySource(properties = {
    "jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:review;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa",
    "jdbc.password="
})
@Sql(scripts = "/review-claim-schema.sql",
    config = @SqlConfig(encoding = "UTF-8"))
public abstract class ReviewTestSupport {

    @Autowired protected ArticleMapper articleMapper;
    @Autowired protected ReviewRecordMapper reviewRecordMapper;
    @Autowired protected OperationLogMapper operationLogMapper;
    @Autowired protected ItemWorkflowService itemWorkflowService;
    @Autowired protected ReviewService reviewService;
    @Autowired protected CategoryAccessService categoryAccessService;
    @Autowired protected AuditService auditService;

    @After
    public void clearIdentity() {
        CurrentUserContext.clear();
        MDC.clear();
    }

    protected void as(long id, String name, String... permissions) {
        Set<String> granted = new LinkedHashSet<>(Arrays.asList(permissions));
        CurrentUserContext.set(new CurrentUser(id, "user" + id, name,
            Collections.emptySet(), granted));
    }

    protected void asRole(long id, String name, String role) {
        CurrentUserContext.set(new CurrentUser(id, "user" + id, name,
            Collections.singleton(role), Collections.emptySet()));
    }

    protected void asAuthor() { as(1L, "张同学"); }
    protected void asApplicant() { as(2L, "李同学"); }
    protected void asReviewer() {
        as(3L, "王审核", ReviewPermissions.REVIEW_HANDLE, "article:publish");
    }
    protected void asAdmin() { asRole(5L, "系统管理员", AuthConstants.ROLE_ADMIN); }

    protected Article article(long id) {
        return articleMapper.selectById(id);
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
