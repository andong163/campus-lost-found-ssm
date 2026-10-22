package com.team.cms.claim.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.claim.dto.ClaimView;
import com.team.cms.claim.entity.ClaimRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * cms_claim_request 数据访问。
 *
 * <p>所有会改变认领状态的动作都先锁定 cms_article 行，再锁定申请行；本接口只提供
 * “锁定读取 + 条件更新”，不在 SQL 中拼接任何请求字符串。状态更新影响 0 行时由服务层转 409。</p>
 */
@Mapper
public interface ClaimRequestMapper extends BaseMapper<ClaimRequest> {

    ClaimRequest selectByIdForUpdate(@Param("id") Long id);

    ClaimRequest selectApprovedForUpdate(@Param("articleId") Long articleId);

    ClaimRequest selectApprovedByItem(@Param("articleId") Long articleId);

    int countActiveByApplicant(@Param("articleId") Long articleId,
            @Param("applicantId") Long applicantId);

    int countActiveByItem(@Param("articleId") Long articleId);

    int countApprovedByItem(@Param("articleId") Long articleId);

    int countApprovedByApplicant(@Param("articleId") Long articleId,
            @Param("applicantId") Long applicantId);

    int updateDecision(@Param("id") Long id,
            @Param("targetStatus") String targetStatus,
            @Param("reviewerId") Long reviewerId,
            @Param("comment") String comment,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("version") int version);

    int updateCancel(@Param("id") Long id,
            @Param("reason") String reason,
            @Param("version") int version);

    int complete(@Param("id") Long id,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("version") int version);

    int closePendingByItem(@Param("articleId") Long articleId,
            @Param("reason") String reason);

    IPage<ClaimView> selectMine(Page<ClaimView> page,
            @Param("applicantId") Long applicantId,
            @Param("status") String status);

    IPage<ClaimView> selectQueue(Page<ClaimView> page,
            @Param("status") String status,
            @Param("itemId") Long itemId);

    List<ClaimView> selectByItem(@Param("articleId") Long articleId);

    ClaimView selectApprovedView(@Param("articleId") Long articleId);
}
