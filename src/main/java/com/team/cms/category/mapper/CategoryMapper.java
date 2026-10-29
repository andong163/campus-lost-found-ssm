package com.team.cms.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.cms.category.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/** cms_category 数据访问。逻辑删除过滤、审计字段填充与乐观锁由 MyBatis-Plus 插件处理。 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM cms_category WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Category selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
