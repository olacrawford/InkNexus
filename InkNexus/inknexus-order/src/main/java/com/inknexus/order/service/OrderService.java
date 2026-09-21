package com.inknexus.order.service;

import com.inknexus.order.dto.OrderCreateRequest;
import com.inknexus.order.dto.OrderFromCartRequest;
import com.inknexus.order.vo.OrderDetailVO;
import com.inknexus.order.vo.OrderVO;

import java.util.List;

/**
 * 订单业务接口：下单（直接/购物车）、查询，以及订单状态机迁移
 * （0 待支付 → 1 已支付 → 3 已完成；0 待支付 → 2 已取消，含超时关单）。
 */
public interface OrderService {

    /**
     * 直接下单：图书不存在或价格为空时返回 null（控制器转 400）。
     */
    OrderDetailVO createOrder(Long userId, OrderCreateRequest request);

    /**
     * 购物车下单：读取购物车已选条目创建多明细订单；无选中条目或图书不存在/已下架时抛 BusinessException。
     */
    OrderDetailVO createOrderFromCart(Long userId, OrderFromCartRequest request);

    List<OrderVO> listOrdersByUserId(Long userId);

    /**
     * 查询订单详情（只能查自己的订单）；订单不存在或不属于该用户返回 null。
     */
    OrderDetailVO getOrderDetail(Long id, Long userId);

    /**
     * 取消订单（只能取消自己的待支付订单）；false 表示订单不存在/非本人/状态不允许。
     */
    boolean cancelOrder(Long id, Long userId);

    /**
     * 标记订单已支付；true 含“已是已支付”的幂等命中。
     */
    boolean markPaid(Long id, Long userId);

    /**
     * 确认收货：只允许当前用户把已支付订单更新为已完成；true 含“已是已完成”的幂等命中。
     */
    boolean completeOrder(Long id, Long userId);

    /**
     * 超时关单并释放预占库存；false 表示订单不存在/已支付/已取消或尚未到期，调用方可安全跳过。
     */
    boolean closeExpiredOrder(Long orderId);
}
