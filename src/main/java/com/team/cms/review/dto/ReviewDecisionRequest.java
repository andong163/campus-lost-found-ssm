package com.team.cms.review.dto;

import com.team.cms.common.api.VersionRequest;
import javax.validation.constraints.Size;

public class ReviewDecisionRequest extends VersionRequest {
    @Size(max=500) private String comment;
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment=comment; }
}
