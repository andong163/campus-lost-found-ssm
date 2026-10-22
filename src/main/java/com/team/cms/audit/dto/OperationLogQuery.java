package com.team.cms.audit.dto;

import com.team.cms.common.api.PageQuery;
import java.time.LocalDateTime;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** 审计日志查询条件；排序固定为时间倒序 + ID 倒序，不接受客户端排序字段。 */
public class OperationLogQuery extends PageQuery {
    private Long operatorId;

    @Size(max = 80, message = "操作者名称过长")
    private String operatorName;

    @Size(max = 80, message = "动作名称过长")
    private String action;

    @Pattern(regexp = "^(SUCCESS|FAILURE)?$", message = "结果只能是 SUCCESS 或 FAILURE")
    private String result;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    @AssertTrue(message = "开始时间不能晚于结束时间")
    public boolean isTimeRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
}
