package com.inknexus.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemCreateRequest {

    // 校验必填参数，避免前端直接传入空 bookId 或非法数量
    @NotNull(message = "图书ID不能为空")
    private Long bookId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    // 可选；不传时由 Service 默认设为勾选
    private Boolean selected;
}
