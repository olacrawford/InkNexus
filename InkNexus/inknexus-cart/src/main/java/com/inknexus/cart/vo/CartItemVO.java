package com.inknexus.cart.vo;

import lombok.Data;

@Data
public class CartItemVO {

    private Long id;
    private Long userId;
    private Long bookId;
    private Integer quantity;
    private Integer selected;
}
