package com.inknexus.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改图书的入参载荷（管理端 PUT /books/{id} 请求体）。
 * <p>与新增相比多一个必填 status（全量替换语义：0 下架、1 上架），
 * 所有字段整体覆盖库中原值。
 */
@Data
public class BookUpdateRequest {

    @NotBlank(message = "书名不能为空")
    private String title;

    private String author;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private String coverUrl;

    private String description;

    /** 上下架状态：1 上架、0 下架 */
    @NotNull(message = "状态不能为空")
    private Integer status;

}