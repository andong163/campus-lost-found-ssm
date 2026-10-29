package com.team.cms.review.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.review.dto.ReviewTaskQuery;
import com.team.cms.review.dto.ReviewTaskView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审核待办查询：只读 cms_article 的 PENDING_REVIEW 视图，不修改内容状态。 */
@Mapper
public interface ReviewTaskMapper {
    IPage<ReviewTaskView> selectPending(Page<ReviewTaskView> page, @Param("q") ReviewTaskQuery query);
}
