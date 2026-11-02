package com.team.cms.review.dto;

import com.team.cms.common.api.VersionRequest;
import javax.validation.constraints.Size;

/** approve/reject 兼容路由：决定来自路由，不让客户端另传冲突的 decision。 */
public class ReviewActionRequest extends VersionRequest {
    @Size(max=500) private String comment;
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment=comment; }
}
