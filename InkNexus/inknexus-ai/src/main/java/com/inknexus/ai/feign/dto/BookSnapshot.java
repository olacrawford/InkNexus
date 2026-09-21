package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 图书数据快照：镜像 book 服务返回的图书字段，AI 模块只读，不落库。 */
@Data
public class BookSnapshot {
    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private String coverUrl;
    private Long categoryId;
    private String description;
    private Integer status;     // 状态：1 在售，0 下架
}