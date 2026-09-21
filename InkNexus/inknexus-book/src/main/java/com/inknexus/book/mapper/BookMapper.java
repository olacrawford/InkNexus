package com.inknexus.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.book.entity.Book;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图书表 Mapper：无自定义 SQL，通用 CRUD（selectList/selectPage/insert/updateById/deleteById）
 * 全部由 MyBatis-Plus {@link BaseMapper} 提供；deleteById 因实体的 @TableLogic 实际执行逻辑删除。
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {
}