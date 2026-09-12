package com.inknexus.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_book_stock")
public class BookStock {

    private Long id;
    private Long bookId;
    private Integer stock;
    private Integer lockedStock;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
