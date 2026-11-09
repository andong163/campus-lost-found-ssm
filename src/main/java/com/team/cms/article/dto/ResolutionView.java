package com.team.cms.article.dto;

import java.time.LocalDateTime;
import com.team.cms.common.domain.ResolutionStatus;

/** 公开历史白名单；不带身份、证据、联系方式或审核意见。 */
public final class ResolutionView {
    private final ResolutionStatus resolutionStatus;
    private final LocalDateTime resolvedAt;
    private final String result;
    public ResolutionView(ResolutionStatus status,LocalDateTime at,String result) {
        this.resolutionStatus=status; this.resolvedAt=at; this.result=result;
    }
    public ResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResult() { return result; }
}
