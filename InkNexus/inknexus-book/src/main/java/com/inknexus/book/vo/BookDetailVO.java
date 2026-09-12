package com.inknexus.book.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
//查询图书详细信息
public class BookDetailVO {

    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private Long categoryId;
    private String coverUrl;
    private String description;
    private Integer status;

    public BookDetailVO() {
    }

    public BookDetailVO(Long id, String title, String author, BigDecimal price,
                        Long categoryId, String coverUrl, String description, Integer status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.price = price;
        this.categoryId = categoryId;
        this.coverUrl = coverUrl;
        this.description = description;
        this.status = status;
    }

}