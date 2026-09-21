package com.inknexus.book.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 图书详情 VO：GET /books/{id} 的返回载荷，也是 {@code BookDetailCache}
 * 详情缓存中序列化存储的对象（JSON 串），字段变更会影响缓存兼容性。
 * 保留无参构造器以满足 JSON 反序列化需要。
 */
@Data
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