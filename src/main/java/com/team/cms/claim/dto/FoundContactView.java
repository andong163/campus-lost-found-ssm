package com.team.cms.claim.dto;

/** FOUND 联系方式的专用响应；只有作者、已通过核实的申请人、认领/审核人员可读。 */
public final class FoundContactView {
    private final String contactInfo;

    public FoundContactView(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getContactInfo() { return contactInfo; }
}
