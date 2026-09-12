package com.inknexus.book.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
//查询图书简单信息
public class BookVO {
    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private String coverUrl;
    private Long categoryId;

    public BookVO() {
    }

    public BookVO(Long id, String title, String author, BigDecimal price, String coverUrl, Long categoryId) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.price = price;
        this.coverUrl = coverUrl;
        this.categoryId = categoryId;
    }

}
