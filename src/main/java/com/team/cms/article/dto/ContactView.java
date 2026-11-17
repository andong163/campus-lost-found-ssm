package com.team.cms.article.dto;

/** 只在单条、经授权、已审计的接口返回。 */
public final class ContactView {
    private final String contactInfo;
    public ContactView(String contactInfo) { this.contactInfo=contactInfo; }
    public String getContactInfo() { return contactInfo; }
}
