package com.inknexus.ai.feign;

import com.inknexus.ai.feign.dto.OrderDetailSnapshot;
import com.inknexus.ai.feign.dto.OrderSnapshot;
import com.inknexus.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** Order 服务 OpenFeign 客户端：只读调用订单服务的查询接口。name = "order" 对应 Nacos 注册的服务名。 */
@FeignClient(name = "order")
public interface OrderFeignClient {

    /** 查询当前登录用户的订单列表。userId 由 Feign 拦截器从 UserContextHolder 注入 X-User-Id 头。 */
    @GetMapping("/orders")
    Result<List<OrderSnapshot>> listOrders();

    /** 查询某笔订单详情。同样会带 X-User-Id，订单服务会校验该订单属于当前用户。 */
    @GetMapping("/orders/{id}")
    Result<OrderDetailSnapshot> getOrderDetail(@PathVariable("id") Long id);
}