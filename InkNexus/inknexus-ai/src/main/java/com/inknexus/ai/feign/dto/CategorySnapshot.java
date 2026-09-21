package com.inknexus.ai.feign.dto;

import lombok.Data;

/** 图书分类数据快照：镜像 book 服务返回的分类字段。 */
@Data
public class CategorySnapshot {
    private Long id;
    private String name;
    private Integer sort;   // 排序权重
}