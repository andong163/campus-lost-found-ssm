package com.team.cms;

import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.config.SpringConfig;
import com.team.cms.review.domain.ReviewDecision;
import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.review.mapper.ReviewRecordMapper;
import com.team.cms.support.ReviewTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 事务回滚验收：让审核记录插入必定失败，确认内容状态不会单独提交。
 * 该测试配置只替换 ReviewRecordMapper 的 insert，其余查询仍走真实 MyBatis。
 */
@ContextConfiguration(classes = {SpringConfig.class, ReviewRollbackTest.FailingConfig.class})
public class ReviewRollbackTest extends ReviewTestSupport {

    @Test
    public void reviewRecordFailureRollsBackArticleStatus() {
        asReviewer();
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setDecision(ReviewDecision.APPROVED);
        request.setVersion(1);
        request.setComment("模拟记录写入失败");

        try {
            reviewService.decide(4L, request);
            fail("应抛出记录写入失败");
        } catch (DataIntegrityViolationException expected) {
            // expected
        }

        assertEquals(ArticleStatus.PENDING_REVIEW, article(4L).getStatus());
        assertEquals(Integer.valueOf(1), article(4L).getVersion());
        assertEquals(0L, reviewRecordMapper.selectCount(null).longValue());
    }

    @Configuration
    static class FailingConfig {
        @Bean
        @Primary
        public ReviewRecordMapper failingReviewRecordMapper(
                @Qualifier("reviewRecordMapper") ReviewRecordMapper delegate) {
            return (ReviewRecordMapper) Proxy.newProxyInstance(
                ReviewRecordMapper.class.getClassLoader(),
                new Class<?>[] {ReviewRecordMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        throw new DataIntegrityViolationException("simulated review record failure");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException error) {
                        throw error.getTargetException();
                    }
                });
        }
    }
}
