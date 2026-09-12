package com.inknexus.book.vo;

import lombok.Data;

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
