package com.team.cms.common.domain;

/** APPROVED means evidence verified; only physical handover can produce COMPLETED. */
public enum ClaimStatus { PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED }
