package com.inknexus.book.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书实体，对应表 t_book（图书主表，含逻辑删除标记列 deleted）。
 * <p>status 语义：1 上架、0 下架——所有对外的查询路径都只取上架数据；
 * deleted 由 MyBatis-Plus 逻辑删除维护，查询自动追加 deleted=0 条件。
 */
@Data
@TableName("t_book")
public class Book {
    /** 主键，即对外暴露的图书 id */
    private Long id;
    private String title;
    private String author;
    /** 定价（元），DECIMAL(10,2) */
    private BigDecimal price;
    /** 所属分类 id，关联 t_category.id */
    private Long categoryId;
    private String coverUrl;
    private String description;
    /** 上下架状态：1 上架、0 下架 */
    private Integer status;

    /** 逻辑删除标记：0 未删、1 已删；deleteById 会被改写为 UPDATE deleted=1 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}