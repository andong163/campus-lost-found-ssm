package com.team.cms;

import com.team.cms.claim.domain.ClaimDecision;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.entity.ClaimRequest;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.support.ReviewClaimTestSupport;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 并发批准验收：两个审核线程同时批准同一物品的两条 PENDING 申请。
 * 物品行锁使两个事务串行，最多一个成功；另一个返回 409，数据库最多一条 APPROVED。
 */
public class ConcurrentClaimApprovalTest extends ReviewClaimTestSupport {

    @Test(timeout = 15000)
    public void onlyOneConcurrentApprovalSucceeds() throws Exception {
        ClaimRequest first = givenClaim(1L, 2L, "PENDING");
        ClaimRequest second = givenClaim(1L, 4L, "PENDING");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> a = pool.submit(approveTask(first.getId(), ready, start));
            Future<String> b = pool.submit(approveTask(second.getId(), ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            String resultA = a.get(10, TimeUnit.SECONDS);
            String resultB = b.get(10, TimeUnit.SECONDS);

            int successes = 0;
            int conflicts = 0;
            for (String result : new String[] {resultA, resultB}) {
                if ("OK".equals(result)) {
                    successes++;
                } else if (result.startsWith("409")) {
                    conflicts++;
                }
            }
            assertEquals("恰好一个批准成功，结果=" + resultA + "/" + resultB, 1, successes);
            assertEquals("另一个应返回409", 1, conflicts);
            assertEquals(1, claimRequestMapper.countApprovedByItem(1L));
            assertEquals(1L, claimRequestMapper.selectList(null).stream()
                .filter(claim -> "APPROVED".equals(claim.getStatus())).count());
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> approveTask(Long claimId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            asReviewer();
            try {
                ClaimDecisionRequest request = new ClaimDecisionRequest();
                request.setDecision(ClaimDecision.APPROVED);
                request.setVersion(0);
                request.setComment("并发批准测试");
                claimService.decide(claimId, request);
                return "OK";
            } catch (BusinessException error) {
                return error.getStatus().value() + ":" + error.getMessage();
            } finally {
                com.team.cms.auth.security.CurrentUserContext.clear();
            }
        };
    }
}
