package com.team.cms.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.article.entity.Article;
import com.team.cms.common.domain.ArticleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    /** 按作者查询私有草稿；调用前仍须验证登录身份和读取权限。 */
    Article selectDraftByIdAndAuthor(@Param("id") long id, @Param("authorId") long authorId);

    /** 在现有事务中先锁定物品行，再处理认领或工作流记录。 */
    Article selectByIdForUpdate(@Param("id") long id);

    /** 工作流使用的原子更新；返回零行可能表示记录不存在、已删除、状态不符或版本过期。 */
    int updateStatusIfVersion(@Param("id") long id,
            @Param("expectedStatus") ArticleStatus expectedStatus,
            @Param("nextStatus") ArticleStatus nextStatus,
            @Param("expectedVersion") int expectedVersion);
}
