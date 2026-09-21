package com.inknexus.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起支付请求体（POST /payment/pay）。
 * 刻意只有订单ID一个字段：支付金额与订单状态由服务端调订单服务获取，不接受前端传入。
 */
@Data
public class PaymentRequest {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}
