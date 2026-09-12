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

    public static final String OPERATION_ORDER_PAID = "ORDER_PAID";
    public static final String OPERATION_ORDER_RELEASE = "ORDER_RELEASE";

    private String eventId = UUID.randomUUID().toString();
    private Long orderId;
    private Long userId;
    private String operation;
    private List<StockItemMessage> items = new ArrayList<>();
}
