package com.inknexus.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增图书的入参载荷（管理端 POST /books 请求体）。
 * <p>不含 status：新增图书固定上架，由 Service 端写死 status=1。
 */
@Data
public class BookCreateRequest {

    /** 书名 */
    @NotBlank(message = "书名不能为空")
    private String title;

    private String author;

    /** 定价（元） */
    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    /** 所属分类 id */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private String coverUrl;

    private String description;

}