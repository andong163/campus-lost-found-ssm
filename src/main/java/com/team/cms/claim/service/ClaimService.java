package com.team.cms.claim.service;

import com.team.cms.claim.dto.ClaimCancelRequest;
import com.team.cms.claim.dto.ClaimCreateRequest;
import com.team.cms.claim.dto.ClaimDecisionRequest;
import com.team.cms.claim.dto.ClaimQuery;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.dto.HandoverView;
import com.team.cms.common.api.PageResult;
import java.util.List;

/** 认领申请、核实、撤销与列表查询。 */
public interface ClaimService {

    ClaimView apply(Long itemId, ClaimCreateRequest request);

    PageResult<ClaimView> mine(ClaimQuery query);

    /** 审核员的认领处理队列；需要 claim:handle。 */
    PageResult<ClaimView> queue(ClaimQuery query);

    /** 发布者或审核人员查看某物品的申请；包含证据，不能进公开 DTO。 */
    List<ClaimView> forItem(Long itemId);

    void decide(Long claimId, ClaimDecisionRequest request);

    void cancel(Long claimId, ClaimCancelRequest request);

    /** 发布者的交接入口：读取物品版本与已通过申请，供页面确认后再调用结案。 */
    HandoverView handoverContext(Long itemId);
}
