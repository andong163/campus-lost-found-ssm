package com.team.cms;

import com.team.cms.common.domain.ArticleStatus;
import com.team.cms.support.ReviewClaimTestSupport;
import java.util.Map;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import static org.junit.Assert.assertEquals;

/** 自定义管理角色只需被授予实际动作权限，不应额外依赖发布权限。 */
public class R3ManagementAuthorizationTest extends ReviewClaimTestSupport {
    @Test public void delegatedOfflinePermissionSupportsSingleAndBatchActions() {
        as(4L, "自定义管理人员", "item:offline:any", "item:batch");
        itemWorkflowService.offline(1L, article(1L).getVersion(), null);
        assertEquals(ArticleStatus.OFFLINE, article(1L).getStatus());
        itemWorkflowService.batch("offline", Map.of(2L, article(2L).getVersion()));
        assertEquals(ArticleStatus.OFFLINE, article(2L).getStatus());
    }

    @Test public void batchFlagAloneCannotGrantOfflineAuthority() {
        as(4L, "无动作权限的管理人员", "item:batch");
        assertStatus(HttpStatus.FORBIDDEN, () -> itemWorkflowService.offline(1L, article(1L).getVersion(), null));
        assertStatus(HttpStatus.FORBIDDEN, () -> itemWorkflowService.batch("offline", Map.of(1L, article(1L).getVersion())));
        assertEquals(ArticleStatus.PUBLISHED, article(1L).getStatus());
    }
}
