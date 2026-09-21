package com.inknexus.book.service;

import com.inknexus.book.vo.CategoryVO;

import java.util.List;

/**
 * 分类业务接口：门户分类导航等只读场景使用。
 */
public interface CategoryService {

    /** 查询启用（status=1）的分类，按 sort 升序 */
    List<CategoryVO> listCategories();
}
