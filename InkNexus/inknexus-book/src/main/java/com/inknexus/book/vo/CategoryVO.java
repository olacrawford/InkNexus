package com.inknexus.book.vo;

import lombok.Data;

/**
 * 分类 VO：GET /books/categories 的返回载荷（id + 名称 + 排序值）。
 */
@Data
public class CategoryVO {

    private Long id;
    private String name;
    private Integer sort;

    public CategoryVO() {
    }

    public CategoryVO(Long id, String name, Integer sort) {
        this.id = id;
        this.name = name;
        this.sort = sort;
    }
}
