package com.inknexus.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车条目请求体（PUT /cart/{id}）。
 * <p>quantity 为覆盖式赋值（直接改成该值，不是在原数量上累加）。
 */
@Data
public class CartItemUpdateRequest {

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    // 是否勾选：null=保持不变；true/false 覆盖为 1/0
    private Boolean selected;
}
