package com.team.cms.claim.dto;

/**
 * 交接入口的只读上下文：发布者据此确认物品版本与已通过申请，再调用 /items/{id}/resolve。
 * 不包含联系方式或认领证据。
 */
public final class HandoverView {
    private final Long itemId;
    private final String title;
    private final String itemType;
    private final String status;
    private final String resolutionStatus;
    private final Integer version;
    private final Long approvedClaimId;
    private final String approvedApplicantName;

    public HandoverView(Long itemId, String title, String itemType, String status,
            String resolutionStatus, Integer version, Long approvedClaimId,
            String approvedApplicantName) {
        this.itemId = itemId;
        this.title = title;
        this.itemType = itemType;
        this.status = status;
        this.resolutionStatus = resolutionStatus;
        this.version = version;
        this.approvedClaimId = approvedClaimId;
        this.approvedApplicantName = approvedApplicantName;
    }

    public Long getItemId() { return itemId; }
    public String getTitle() { return title; }
    public String getItemType() { return itemType; }
    public String getStatus() { return status; }
    public String getResolutionStatus() { return resolutionStatus; }
    public Integer getVersion() { return version; }
    public Long getApprovedClaimId() { return approvedClaimId; }
    public String getApprovedApplicantName() { return approvedApplicantName; }
}
