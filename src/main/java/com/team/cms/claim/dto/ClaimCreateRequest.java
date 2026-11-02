package com.team.cms.claim.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 学生提交认领：只提交私有说明与联系方式，申请人由服务端会话确定。 */
public class ClaimCreateRequest {
    @NotBlank(message = "认领说明不能为空")
    @Size(min = 10, max = 1000, message = "认领说明需为10到1000字")
    private String claimDescription;

    @NotBlank(message = "联系方式不能为空")
    @Size(min = 2, max = 100, message = "联系方式需为2到100字")
    private String contactInfo;

    public String getClaimDescription() { return claimDescription; }
    public void setClaimDescription(String claimDescription) { this.claimDescription = claimDescription; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
}
