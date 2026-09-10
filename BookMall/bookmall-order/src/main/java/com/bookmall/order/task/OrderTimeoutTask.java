package com.bookmall.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bookmall.order.entity.Order;
import com.bookmall.order.mapper.OrderMapper;
import com.bookmall.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时兜底任务：主链路由 RabbitMQ 延迟消息（TTL+死信）关单，本任务只兜底
 * 清理延迟消息发送失败或 RabbitMQ 不可用期间漏网的超时订单。
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final int batchSize;

    public OrderTimeoutTask(OrderMapper orderMapper,
                            OrderService orderService,
                            @Value("${bookmall.order.close-batch-size:500}") int batchSize) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${bookmall.order.close-cron:0 */2 * * * ?}")
    public void closeExpiredOrders() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0)
                .isNotNull(Order::getExpireTime)
                .le(Order::getExpireTime, LocalDateTime.now())
                .orderByAsc(Order::getExpireTime)
                .last("LIMIT " + batchSize));

        for (Order order : orders) {
            try {
                if (orderService.closeExpiredOrder(order.getId())) {
                    log.info("自动关闭超时订单：orderId={}", order.getId());
                }
            } catch (RuntimeException ex) {
                // 保留库存状态和订单状态，等待下一轮重试
                log.warn("自动关闭超时订单失败：orderId={}", order.getId(), ex);
            }
        }
    }
}
