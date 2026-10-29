package com.team.cms.review.service;

import com.team.cms.review.dto.ReviewDecisionRequest;
import com.team.cms.review.dto.ReviewHistoryQuery;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.dto.ReviewTaskQuery;
import com.team.cms.review.dto.ReviewTaskView;
import com.team.cms.common.api.PageResult;
import java.util.List;

/** 审核工作台服务：待办、历史、决定、单物品审核记录。 */
public interface ReviewService {
    PageResult<ReviewTaskView> pending(ReviewTaskQuery query);

    PageResult<ReviewRecordView> history(ReviewHistoryQuery query);

    /** 批准或驳回；不能审核本人内容，状态与记录同事务。 */
    void decide(Long itemId, ReviewDecisionRequest request);

    /** 作者或审核人员查看某物品的审核记录。 */
    List<ReviewRecordView> records(Long itemId);
}
