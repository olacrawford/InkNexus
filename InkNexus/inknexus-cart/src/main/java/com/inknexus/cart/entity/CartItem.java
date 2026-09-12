package com.inknexus.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_cart_item")
public class CartItem {

    // 对应 t_cart_item 表，userId + bookId 唯一，重复加购时复用同一行
    private Long id;
    private Long userId;
    private Long bookId;
    private Integer quantity;
    private Integer selected;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
