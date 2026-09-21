package com.inknexus.order.client;

import com.inknexus.common.result.Result;
import com.inknexus.order.client.dto.CartItemSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 购物车服务 Feign 客户端：购物车下单前读取当前用户已勾选的条目。
 */
@FeignClient(name = "cart")
public interface CartClient {

    @GetMapping("/cart/selected")
    Result<List<CartItemSnapshot>> selectedItems(@RequestHeader("X-User-Id") Long userId);
}
