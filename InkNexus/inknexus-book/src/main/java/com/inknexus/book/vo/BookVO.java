package com.inknexus.book.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书列表项 VO：列表 / 分页 / 按 id 批量查询（GET /books、/books/page、/books/by-ids）的返回载荷。
 * <p>只带封面级简单字段，不含简介等重内容；BookDetailVO 才是详情载荷。
 * 保留无参构造器以满足 JSON 反序列化需要。
 */
@Data
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
