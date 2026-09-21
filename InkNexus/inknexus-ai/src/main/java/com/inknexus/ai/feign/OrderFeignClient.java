package com.inknexus.ai.feign;

import com.inknexus.ai.feign.dto.OrderDetailSnapshot;
import com.inknexus.ai.feign.dto.OrderSnapshot;
import com.inknexus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Order 服务 OpenFeign 客户端：只读调用订单服务的查询接口。name = "order" 对应 Nacos 注册的服务名。
 * 两个接口都依赖 X-User-Id 头（由 FeignAuthConfig 注入）确定“当前用户”，下游只返回该用户名下的订单。
 */
@FeignClient(name = "order")
public interface OrderFeignClient {

    /** 查询当前登录用户的订单列表。 */
    @GetMapping("/orders")
    Result<List<OrderSnapshot>> listOrders();

    /** 查询某笔订单详情，订单服务会校验归属。 */
    @GetMapping("/orders/{id}")
    Result<OrderDetailSnapshot> getOrderDetail(@PathVariable("id") Long id);
}