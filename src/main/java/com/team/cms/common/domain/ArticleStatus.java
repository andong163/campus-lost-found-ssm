package com.team.cms.common.domain;

/** Agreed persistence values; transitions/authorization are implemented by article/review modules. */
public enum ArticleStatus { DRAFT, PENDING_REVIEW, APPROVED, PUBLISHED, REJECTED, OFFLINE }
