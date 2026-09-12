package com.inknexus.ai.feign.dto;

import lombok.Data;

/** 图书分类数据快照：镜像 book 服务返回的分类字段。 */
@Data
public class CategorySnapshot {
    private Long id;        // 分类 ID
    private String name;    // 分类名称
    private Integer sort;   // 排序权重
}