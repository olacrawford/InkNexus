package com.inknexus.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.book.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类表 Mapper：无自定义 SQL，通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
