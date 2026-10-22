package com.team.cms.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.article.entity.Article;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * cms_article 数据访问契约。
 *
 * <p>普通读取复用 MyBatis-Plus BaseMapper；状态流转必须使用本接口的
 * {@link #selectByIdForUpdate(Long)} + {@link #updateStatus} 组合：先锁定物品行，
 * 再校验状态与 version，最后带原状态和原版本条件更新。影响行数为 0 时一律转 409，
 * 不能假定乐观锁插件会自动覆盖自定义 SQL。</p>
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 锁定单条物品行；逻辑删除的记录视为不存在。必须在事务中调用。 */
    Article selectByIdForUpdate(@Param("id") Long id);

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
