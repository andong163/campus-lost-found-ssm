package com.team.cms.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.review.dto.ReviewHistoryQuery;
import com.team.cms.review.dto.ReviewRecordView;
import com.team.cms.review.entity.ReviewRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审核记录数据访问；记录只追加，不逻辑删除。 */
@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {
    List<ReviewRecordView> selectViewsByArticleId(@Param("articleId") Long articleId);

    IPage<ReviewRecordView> selectHistory(Page<ReviewRecordView> page,
            @Param("q") ReviewHistoryQuery query);
}
