package com.inknexus.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 订单状态变化事件，由订单服务(OrderEventPublisher)发布、库存服务(OrderStockConsumer)消费，
 * 驱动预占库存的确认扣减（支付成功）或释放（取消/关单）。
 *
 * <p>幂等分两层：消息层按 eventId 在 t_mq_consumed_log 去重（去重插入与库存更新同一事务），
 * 业务层由库存 SQL 条件更新兜底，两层合起来保证 MQ 重投不会重复确认/释放库存。</p>
 */
@Data
@NoArgsConstructor
public class OrderStockEvent {

    // 已支付：库存侧把预占库存转为确认扣减
    public static final String OPERATION_ORDER_PAID = "ORDER_PAID";
    // 取消/关单：库存侧释放预占库存
    public static final String OPERATION_ORDER_RELEASE = "ORDER_RELEASE";

    private String eventId = UUID.randomUUID().toString();
    private Long orderId;
    private Long userId;
    /** 操作类型，取值仅限 OPERATION_ORDER_PAID / OPERATION_ORDER_RELEASE，决定 items 的动作语义 */
    private String operation;
    /** 涉及的商品条目，quantity 为正数，动作（确认/释放）由 operation 决定 */
    private List<StockItemMessage> items = new ArrayList<>();
}
