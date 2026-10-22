package com.team.cms;

import com.team.cms.audit.dto.OperationLogQuery;
import com.team.cms.audit.dto.OperationLogView;
import com.team.cms.audit.service.AuditEntry;
import com.team.cms.common.api.PageResult;
import com.team.cms.support.AuditTestSupport;
import java.time.LocalDateTime;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;

/** 审计写入与授权查询的真实 MyBatis/H2 测试。 */
public class AuditServiceTest extends AuditTestSupport {

    @Test
    public void writeAndQueryByOperatorActionResultAndTime() {
        LocalDateTime now = LocalDateTime.now();
        auditService.write(new AuditEntry(1L, "张同学", "review", "REVIEW_DECIDE", 10L,
            "req-1", "SUCCESS", 12L, now.minusMinutes(2)));
        auditService.write(new AuditEntry(2L, "李同学", "claim", "CLAIM_APPLY", 11L,
            "req-2", "FAILURE", 3L, now.minusMinutes(1)));

        OperationLogQuery byOperator = new OperationLogQuery();
        byOperator.setOperatorId(1L);
        PageResult<OperationLogView> operatorPage = auditService.query(byOperator);
        assertEquals(1L, operatorPage.getTotal());
        assertEquals("REVIEW_DECIDE", operatorPage.getRecords().get(0).getAction());

        OperationLogQuery byAction = new OperationLogQuery();
        byAction.setAction("CLAIM");
        assertEquals(1L, auditService.query(byAction).getTotal());

        OperationLogQuery byResult = new OperationLogQuery();
        byResult.setResult("FAILURE");
        PageResult<OperationLogView> failed = auditService.query(byResult);
        assertEquals(1L, failed.getTotal());
        assertEquals("req-2", failed.getRecords().get(0).getRequestId());

        OperationLogQuery future = new OperationLogQuery();
        future.setFrom(now.plusMinutes(1));
        assertEquals(0L, auditService.query(future).getTotal());
    }

    @Test
    public void querySortsNewestFirstAndClampsOversizedAction() {
        auditService.write(new AuditEntry(1L, "张同学", "review", "A" + "x".repeat(120), 1L,
            "req-a", "SUCCESS", 1L, LocalDateTime.now().minusMinutes(1)));
        auditService.write(new AuditEntry(1L, "张同学", "review", "REVIEW_DECIDE", 2L,
            "req-b", "SUCCESS", 1L, LocalDateTime.now()));

        PageResult<OperationLogView> page = auditService.query(new OperationLogQuery());
        assertEquals(2L, page.getTotal());
        assertEquals("REVIEW_DECIDE", page.getRecords().get(0).getAction());
        assertEquals(80, page.getRecords().get(1).getAction().length());
    }

    @Test
    public void invertedTimeRangeIsRejected() {
        OperationLogQuery query = new OperationLogQuery();
        query.setFrom(LocalDateTime.now());
        query.setTo(LocalDateTime.now().minusMinutes(5));
        assertStatus(HttpStatus.BAD_REQUEST, () -> auditService.query(query));
    }
}
