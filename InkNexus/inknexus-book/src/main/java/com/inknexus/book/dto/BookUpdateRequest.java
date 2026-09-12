package com.inknexus.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

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

    @NotNull(message = "状态不能为空")
    private Integer status;

}