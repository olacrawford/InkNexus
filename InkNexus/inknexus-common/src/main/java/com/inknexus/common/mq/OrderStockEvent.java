package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 订单状态变化事件，由订单服务发布、库存服务消费。
 */
@Data
@NoArgsConstructor
public class OrderStockEvent {

    // 已支付：库存侧把预占库存转为确认扣减
    public static final String OPERATION_ORDER_PAID = "ORDER_PAID";
    // 取消/关单：库存侧释放预占库存
    public static final String OPERATION_ORDER_RELEASE = "ORDER_RELEASE";

    // 消费侧按 eventId 幂等（t_mq_consumed_log 去重），防 MQ 重投导致库存重复确认/释放
    private String eventId = UUID.randomUUID().toString();
    private Long orderId;
    private Long userId;
    private String operation;
    private List<StockItemMessage> items = new ArrayList<>();
}
