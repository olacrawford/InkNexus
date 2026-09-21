package com.inknexus.payment.client;

import com.inknexus.common.result.Result;
import com.inknexus.payment.client.dto.OrderSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务 Feign 客户端：支付前的订单校验走这里。
 * 支付服务不直接读订单库，金额、状态、归属一律以订单服务返回为准。
 */
@FeignClient(name = "order")
public interface OrderClient {

    /**
     * 查询订单详情快照，用于支付前的归属、金额与状态校验。
     * userId 经 X-User-Id 请求头透传给订单服务做归属校验。
     */
    @GetMapping("/orders/{id}")
    Result<OrderSnapshot> getOrderDetail(@PathVariable("id") Long id,
                                         @RequestHeader("X-User-Id") Long userId);
}
