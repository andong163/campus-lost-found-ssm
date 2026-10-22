package com.team.cms.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.ArticleStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    /** 按作者查询私有草稿；调用前仍须验证登录身份和读取权限。 */
    Article selectDraftByIdAndAuthor(@Param("id") long id, @Param("authorId") long authorId);

    /** 在现有事务中先锁定物品行，再处理认领或工作流记录。 */
    Article selectByIdForUpdate(@Param("id") Long id);

    /** 工作流使用的原子更新；返回零行可能表示记录不存在、已删除、状态不符或版本过期。 */
    int updateStatusIfVersion(@Param("id") long id,
            @Param("expectedStatus") ArticleStatus expectedStatus,
            @Param("nextStatus") ArticleStatus nextStatus,
            @Param("expectedVersion") int expectedVersion);

    /**
     * 条件状态更新：仅当 id、当前状态、版本都匹配时成功，并将 version + 1。
     * 首次发布或重新发布时传 publishedAt，其他动作传 null。
     *
     * @return 影响行数；0 表示状态或版本已变化，应返回 409
     */
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("targetStatus") String targetStatus,
                     @Param("version") int version,
                     @Param("publishedAt") LocalDateTime publishedAt);

    /**
     * 条件结案：仅 OPEN 且版本匹配时写入 RESOLVED 与 resolved_at，并将 version + 1。
     *
     * @return 影响行数；0 表示已结案或版本过期
     */
    int resolve(@Param("id") Long id,
                @Param("version") int version,
                @Param("resolvedAt") LocalDateTime resolvedAt);
}
