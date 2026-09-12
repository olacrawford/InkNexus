package com.inknexus.ai.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 图书数据快照：镜像 book 服务返回的图书字段，AI 模块只读，不落库。 */
@Data
public class BookSnapshot {
    private Long id;            // 图书 ID
    private String title;       // 书名
    private String author;      // 作者
    private BigDecimal price;   // 价格
    private String coverUrl;    // 封面地址
    private Long categoryId;    // 分类 ID
    private String description; // 简介
    private Integer status;     // 状态：1 在售，0 下架
}