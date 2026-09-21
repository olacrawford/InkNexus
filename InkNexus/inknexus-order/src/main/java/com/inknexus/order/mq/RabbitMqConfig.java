package com.inknexus.order.mq;

import com.inknexus.common.mq.InkNexusRabbitMq;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务声明支付成功消费队列、订单状态事件队列和超时关单延迟队列。
 * 业务队列统一挂死信交换机：消费重试耗尽的消息进入 {@code inknexus.dlx.queue} 等待人工处理。
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    /**
     * 支付成功事件交换机：payment 服务发布支付成功消息、本服务消费的入口拓扑。
     */
    @Bean
    public TopicExchange paySuccessExchange() {
        return new TopicExchange(InkNexusRabbitMq.PAY_SUCCESS_EXCHANGE, true, false);
    }

    /**
     * 支付成功消费队列，由 {@link PaySuccessConsumer} 监听，驱动订单状态 0→1。
     */
    @Bean
    public Queue paySuccessQueue() {
        // payment 服务声明同名队列，死信参数必须与这里完全一致（RabbitMQ 等价声明校验）
        return QueueBuilder.durable(InkNexusRabbitMq.PAY_SUCCESS_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_PAY_SUCCESS)
                .build();
    }

    @Bean
    public Binding paySuccessBinding(TopicExchange paySuccessExchange, Queue paySuccessQueue) {
        return BindingBuilder.bind(paySuccessQueue)
                .to(paySuccessExchange)
                .with(InkNexusRabbitMq.PAY_SUCCESS_ROUTING_KEY);
    }

    /**
     * 订单-库存事件交换机：库存确认（订单已支付）与库存释放（取消/超时/补偿）事件都从这里路由给 stock 服务。
     */
    @Bean
    public TopicExchange orderStockExchange() {
        return new TopicExchange(InkNexusRabbitMq.ORDER_STOCK_EXCHANGE, true, false);
    }

    /**
     * 库存确认队列：由 stock 服务消费，把预占库存转为实际扣减。
     */
    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_PAID_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_ORDER_PAID)
                .build();
    }

    /**
     * 库存释放队列：由 stock 服务消费，归还用户取消、超时关单、下单补偿场景下的预占库存。
     */
    @Bean
    public Queue orderStockReleaseQueue() {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_STOCK_RELEASE_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_STOCK_RELEASE)
                .build();
    }

    @Bean
    public Binding orderPaidBinding(TopicExchange orderStockExchange, Queue orderPaidQueue) {
        return BindingBuilder.bind(orderPaidQueue)
                .to(orderStockExchange)
                .with(InkNexusRabbitMq.ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public Binding orderStockReleaseBinding(TopicExchange orderStockExchange, Queue orderStockReleaseQueue) {
        return BindingBuilder.bind(orderStockReleaseQueue)
                .to(orderStockExchange)
                .with(InkNexusRabbitMq.ORDER_STOCK_RELEASE_ROUTING_KEY);
    }

    /**
     * 超时关单拓扑：订单创建后发往延迟队列，TTL 过期后死信投递到关单队列。
     * 所有订单过期时间统一为 expire-minutes，因此用队列级 TTL 而非按消息 TTL，
     * 规避单队列内按消息 TTL 的队头阻塞问题。
     * 注意：RabbitMQ 队列参数一经声明不可更改，调整 expire-minutes 后需删除旧队列让其重建。
     */
    @Bean
    public Queue orderCloseDelayQueue(
            @Value("${inknexus.order.expire-minutes:30}") int expireMinutes) {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_CLOSE_DELAY_QUEUE)
                .withArgument("x-message-ttl", expireMinutes * 60 * 1000)
                .withArgument("x-dead-letter-exchange", InkNexusRabbitMq.ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", InkNexusRabbitMq.ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(InkNexusRabbitMq.ORDER_CLOSE_EXCHANGE, true, false);
    }

    /**
     * 关单队列：延迟队列 TTL 到期后死信投递到这里，由本服务 {@link OrderCloseDelayConsumer} 消费。
     */
    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(InkNexusRabbitMq.ORDER_CLOSE_QUEUE)
                .deadLetterExchange(InkNexusRabbitMq.DLX_EXCHANGE)
                .deadLetterRoutingKey(InkNexusRabbitMq.DLX_KEY_ORDER_CLOSE)
                .build();
    }

    @Bean
    public Binding orderCloseBinding(DirectExchange orderCloseExchange, Queue orderCloseQueue) {
        return BindingBuilder.bind(orderCloseQueue)
                .to(orderCloseExchange)
                .with(InkNexusRabbitMq.ORDER_CLOSE_ROUTING_KEY);
    }

    // ===== 死信拓扑：消费重试耗尽的消息统一落地，等待人工排查 =====

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(InkNexusRabbitMq.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(InkNexusRabbitMq.DLX_QUEUE, true);
    }

    @Bean
    public Binding dlxPaySuccessBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_PAY_SUCCESS);
    }

    @Bean
    public Binding dlxOrderPaidBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_ORDER_PAID);
    }

    @Bean
    public Binding dlxStockReleaseBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_STOCK_RELEASE);
    }

    @Bean
    public Binding dlxOrderCloseBinding(DirectExchange dlxExchange, Queue dlxQueue) {
        return BindingBuilder.bind(dlxQueue)
                .to(dlxExchange)
                .with(InkNexusRabbitMq.DLX_KEY_ORDER_CLOSE);
    }
}
