package com.inknexus.book.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书分类实体，对应表 t_category。
 * <p>无逻辑删除列；status 语义：1 启用、0 禁用，对外查询只取启用分类。
 */
@Data
@TableName("t_category")
public class Category {

    private Long id;
    private String name;
    /** 展示排序值，值小的排前面 */
    private Integer sort;
    /** 启用状态：1 启用、0 禁用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
