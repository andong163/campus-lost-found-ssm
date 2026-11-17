package com.team.cms;

import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.common.domain.ResolutionStatus;
import com.team.cms.config.SpringConfig;
import com.team.cms.support.ReviewClaimTestSupport;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * 交接事务回滚验收：认领先变 COMPLETED、随后物品结案失败时必须整体回滚，
 * 不能出现“申请完成了但物品仍 OPEN”或相反的部分成功。
 */
@ContextConfiguration(classes = {SpringConfig.class, HandoverRollbackTest.FailingConfig.class})
public class HandoverRollbackTest extends ReviewClaimTestSupport {

    @Test
    public void failingArticleResolveRollsBackClaimCompletion() {
        asApplicant();
        ClaimCreateRequest apply = new ClaimCreateRequest();
        apply.setClaimDescription("我丢失的物品与描述一致，可提供购买记录和照片。");
        apply.setContactInfo("13900000002");
        ClaimView claim = claimService.apply(1L, apply);

        asReviewer();
        ClaimDecisionRequest approve = new ClaimDecisionRequest();
        approve.setDecision(ClaimDecision.APPROVED);
        approve.setVersion(0);
        approve.setComment("特征吻合");
        claimService.decide(claim.getId(), approve);

        int articleVersion = article(1L).getVersion();
        asAuthor();
        try {
            itemWorkflowService.resolve(1L, articleVersion, claim.getId());
            fail("应抛出物品结案失败");
        } catch (DataIntegrityViolationException expected) {
            // expected
        }

        assertEquals("APPROVED", claim(claim.getId()).getStatus());
        assertNull(claim(claim.getId()).getCompletedAt());
        assertEquals(ResolutionStatus.OPEN, article(1L).getResolutionStatus());
        assertNull(article(1L).getResolvedAt());
        assertEquals(Integer.valueOf(articleVersion), article(1L).getVersion());
    }

    @Configuration
    static class FailingConfig {
        @Bean
        @Primary
        public ArticleMapper failingArticleMapper(@Qualifier("articleMapper") ArticleMapper delegate) {
            return (ArticleMapper) Proxy.newProxyInstance(
                ArticleMapper.class.getClassLoader(),
                new Class<?>[] {ArticleMapper.class},
                (proxy, method, args) -> {
                    if ("resolve".equals(method.getName())) {
                        throw new DataIntegrityViolationException("simulated article resolve failure");
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
