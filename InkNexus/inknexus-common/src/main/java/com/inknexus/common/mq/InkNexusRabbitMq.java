package com.inknexus.common.mq;

/**
 * InkNexus RabbitMQ 消息拓扑常量。
 */
public final class InkNexusRabbitMq {

    private InkNexusRabbitMq() {
    }

    // 支付成功事件：payment -> order
    public static final String PAY_SUCCESS_EXCHANGE = "inknexus.pay.success.exchange";
    public static final String PAY_SUCCESS_QUEUE = "inknexus.order.pay.success.queue";
    public static final String PAY_SUCCESS_ROUTING_KEY = "pay.success";

    // 订单状态事件：order -> stock
    public static final String ORDER_STOCK_EXCHANGE = "inknexus.order.stock.exchange";
    public static final String ORDER_PAID_QUEUE = "inknexus.stock.order.paid.queue";
    public static final String ORDER_STOCK_RELEASE_QUEUE = "inknexus.stock.order.release.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
    public static final String ORDER_STOCK_RELEASE_ROUTING_KEY = "order.stock.release";

    // 订单超时关单延迟消息：order 下单时发往延迟队列，TTL 过期后经死信交换机进入关单队列，order 自行消费
    public static final String ORDER_CLOSE_EXCHANGE = "inknexus.order.close.exchange";
    public static final String ORDER_CLOSE_DELAY_QUEUE = "inknexus.order.close.delay.queue";
    public static final String ORDER_CLOSE_QUEUE = "inknexus.order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";
}
