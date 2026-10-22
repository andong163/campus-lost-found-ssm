package com.team.cms.review.domain;

/** 审核决定；只允许批准或驳回，不能由客户端指定任意目标状态。 */
public enum ReviewDecision { APPROVED, REJECTED }
