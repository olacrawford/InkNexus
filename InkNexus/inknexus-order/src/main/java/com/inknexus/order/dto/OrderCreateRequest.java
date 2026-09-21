package com.inknexus.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 直接下单请求体（POST /orders）。
 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "图书ID不能为空")
    private Long bookId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /** 客户端请求号（可选）：同一用户重复提交返回同一笔订单（下单幂等） */
    @Size(max = 64, message = "客户端请求号最长64位")
    private String clientRequestId;

}
