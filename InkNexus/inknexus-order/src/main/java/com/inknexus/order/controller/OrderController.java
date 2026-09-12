package com.inknexus.order.controller;

import com.inknexus.common.result.Result;
import com.inknexus.order.dto.OrderCreateRequest;
import com.inknexus.order.dto.OrderFromCartRequest;
import com.inknexus.order.service.OrderService;
import com.inknexus.order.vo.OrderDetailVO;
import com.inknexus.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单接口
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 健康检查
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-order is running");
    }

    // 下单：userId 由网关鉴权后透传，不从请求体取
    @PostMapping
    public Result<OrderDetailVO> createOrder(@RequestHeader("X-User-Id") Long userId,
                                             @Valid @RequestBody OrderCreateRequest request) {
        OrderDetailVO detail = orderService.createOrder(userId, request);
        if (detail == null) {
            return Result.fail(400, "下单失败，请检查图书信息");
        }
        return Result.success(detail);
    }

    // 购物车下单：只接收收货信息，图书和数量来自购物车中已选条目
    @PostMapping("/from-cart")
    public Result<OrderDetailVO> createOrderFromCart(@RequestHeader("X-User-Id") Long userId,
                                                     @Valid @RequestBody OrderFromCartRequest request) {
        return Result.success(orderService.createOrderFromCart(userId, request));
    }

    // 查询当前用户的订单列表
    @GetMapping
    public Result<List<OrderVO>> listOrders(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.listOrdersByUserId(userId));
    }

    // 查询订单详情
    @GetMapping("/{id}")
    public Result<OrderDetailVO> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                                @PathVariable("id") Long id) {
        OrderDetailVO detail = orderService.getOrderDetail(id, userId);
        if (detail == null) {
            return Result.fail(404, "订单不存在");
        }
        return Result.success(detail);
    }

    // 取消订单
    @PutMapping("/{id}/cancel")
    public Result<String> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable("id") Long id) {
        boolean cancelled = orderService.cancelOrder(id, userId);
        if (!cancelled) {
            return Result.fail(404, "订单不存在");
        }
        return Result.success("取消成功");
    }

    // 手工验证接口；正常支付链路通过 RabbitMQ 支付成功事件处理
    @PutMapping("/{id}/paid")
    public Result<String> markPaid(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable("id") Long id) {
        boolean paid = orderService.markPaid(id, userId);
        if (!paid) {
            return Result.fail(404, "订单不存在");
        }
        return Result.success("支付成功");
    }

    // 确认收货：只允许当前用户把已支付订单标记为已完成
    @PutMapping("/{id}/complete")
    public Result<String> completeOrder(@RequestHeader("X-User-Id") Long userId,
                                        @PathVariable("id") Long id) {
        boolean completed = orderService.completeOrder(id, userId);
        if (!completed) {
            return Result.fail(404, "订单不存在或不可确认收货");
        }
        return Result.success("确认收货成功");
    }
}
