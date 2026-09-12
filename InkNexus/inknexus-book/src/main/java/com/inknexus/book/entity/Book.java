package com.inknexus.book.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_book")
public class Book {
    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private Long categoryId;
    private String coverUrl;
    private String description;
    private Integer status;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}