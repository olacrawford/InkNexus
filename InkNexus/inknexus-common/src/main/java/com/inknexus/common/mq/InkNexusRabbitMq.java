package com.inknexus.common.mq;

/**
 * InkNexus RabbitMQ 消息拓扑常量：交换机/队列/routing key 集中在此定义，
 * 各模块声明拓扑与收发消息都引用本类，避免字符串写散导致对不上。
 * 全系统四条消息链路（支付成功、订单库存事件、超时关单、死信兜底）见下方各常量分组注释。
 *
 * <p>同一队列常由"生产方 + 消费方"两个服务重复声明（RabbitMQ 幂等声明，参数必须完全一致，
 * 否则等价性校验启动报错），因此改名/改参数时必须两侧同步；死信 key 与业务队列一一对应，
 * 标记消息来自哪条链路。</p>
 */
public final class InkNexusRabbitMq {

    private InkNexusRabbitMq() {
    }

    // 支付成功事件：payment -> order。
    // 交换机/队列由 order 与 payment 双方等价声明（队列挂死信 key dlx.pay.success），
    // 载荷为 PaySuccessMessage
    public static final String PAY_SUCCESS_EXCHANGE = "inknexus.pay.success.exchange";
    public static final String PAY_SUCCESS_QUEUE = "inknexus.order.pay.success.queue";
    public static final String PAY_SUCCESS_ROUTING_KEY = "pay.success";

    // 订单状态事件：order -> stock。Topic 交换机按 routing key 分流到两条队列：
    // order.paid -> ORDER_PAID_QUEUE（预占库存转确认扣减），
    // order.stock.release -> ORDER_STOCK_RELEASE_QUEUE（取消/关单释放预占），
    // 载荷均为 OrderStockEvent
    public static final String ORDER_STOCK_EXCHANGE = "inknexus.order.stock.exchange";
    public static final String ORDER_PAID_QUEUE = "inknexus.stock.order.paid.queue";
    public static final String ORDER_STOCK_RELEASE_QUEUE = "inknexus.stock.order.release.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
    public static final String ORDER_STOCK_RELEASE_ROUTING_KEY = "order.stock.release";

    // 订单超时关单延迟消息：order 下单时发往 ORDER_CLOSE_DELAY_QUEUE，队列级 TTL（expire-minutes）到期后
    // 由 x-dead-letter-exchange 投递到 ORDER_CLOSE_EXCHANGE，经 routing key order.close 进入 ORDER_CLOSE_QUEUE，
    // order(OrderCloseDelayConsumer) 自行消费关单。用队列级 TTL 而非消息级 TTL，规避同队列按消息 TTL 的队头阻塞
    public static final String ORDER_CLOSE_EXCHANGE = "inknexus.order.close.exchange";
    public static final String ORDER_CLOSE_DELAY_QUEUE = "inknexus.order.close.delay.queue";
    public static final String ORDER_CLOSE_QUEUE = "inknexus.order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    // 死信拓扑：业务队列消费重试耗尽的消息统一进入死信队列，等待人工排查后处理。
    // DLX_KEY_* 与四条业务队列一一对应；当前无自动消费者，消息滞留队列由人工介入
    public static final String DLX_EXCHANGE = "inknexus.dlx.exchange";
    public static final String DLX_QUEUE = "inknexus.dlx.queue";
    public static final String DLX_KEY_PAY_SUCCESS = "dlx.pay.success";
    public static final String DLX_KEY_ORDER_PAID = "dlx.order.paid";
    public static final String DLX_KEY_STOCK_RELEASE = "dlx.order.stock.release";
    public static final String DLX_KEY_ORDER_CLOSE = "dlx.order.close";
}
