package com.bookmall.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderFromCartRequest {

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /**
     * 客户端请求号，可选。传入后同一用户重复提交返回同一笔订单（下单幂等）。
     */
    @Size(max = 64, message = "客户端请求号最长64位")
    private String clientRequestId;
}
