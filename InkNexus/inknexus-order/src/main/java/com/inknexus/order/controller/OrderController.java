package com.inknexus.order.controller;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.result.Result;
import com.inknexus.order.dto.OrderCreateRequest;
import com.inknexus.order.dto.OrderFromCartRequest;
import com.inknexus.order.service.OrderService;
import com.inknexus.order.vo.OrderDetailVO;
import com.inknexus.order.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单 HTTP 接口：直接下单、购物车下单、列表/详情查询、取消与确认收货。
 * 用户身份一律取网关鉴权后透传的 X-User-Id 请求头，不信任任何请求参数里的用户标识。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-order is running");
    }

    @PostMapping
    public Result<OrderDetailVO> createOrder(@RequestHeader("X-User-Id") Long userId,
                                             @Valid @RequestBody OrderCreateRequest request) {
        OrderDetailVO detail = orderService.createOrder(userId, request);
        if (detail == null) {
            return Result.fail(ErrorCode.ORDER_PLACE_FAILED);
        }
        return Result.success(detail);
    }

    @PostMapping("/from-cart")
    public Result<OrderDetailVO> createOrderFromCart(@RequestHeader("X-User-Id") Long userId,
                                                     @Valid @RequestBody OrderFromCartRequest request) {
        return Result.success(orderService.createOrderFromCart(userId, request));
    }

    @GetMapping
    public Result<List<OrderVO>> listOrders(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.listOrdersByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                                @PathVariable("id") Long id) {
        OrderDetailVO detail = orderService.getOrderDetail(id, userId);
        if (detail == null) {
            return Result.fail(ErrorCode.NOT_FOUND);
        }
        return Result.success(detail);
    }

    @PutMapping("/{id}/cancel")
    public Result<String> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable("id") Long id) {
        boolean cancelled = orderService.cancelOrder(id, userId);
        // false 可能是订单不存在、非本人或状态不允许，对外统一按 404 语义返回
        if (!cancelled) {
            return Result.fail(ErrorCode.NOT_FOUND);
        }
        return Result.success("取消成功");
    }

    @PutMapping("/{id}/complete")
    public Result<String> completeOrder(@RequestHeader("X-User-Id") Long userId,
                                        @PathVariable("id") Long id) {
        boolean completed = orderService.completeOrder(id, userId);
        if (!completed) {
            return Result.fail(ErrorCode.NOT_FOUND);
        }
        return Result.success("确认收货成功");
    }
}
