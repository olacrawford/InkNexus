package com.inknexus.payment.client;

import com.inknexus.common.result.Result;
import com.inknexus.payment.client.dto.OrderSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "order")
public interface OrderClient {

    // 通过订单服务校验订单归属、金额和当前状态
    @GetMapping("/orders/{id}")
    Result<OrderSnapshot> getOrderDetail(@PathVariable("id") Long id,
                                         @RequestHeader("X-User-Id") Long userId);
}
