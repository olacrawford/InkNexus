package com.inknexus.order.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 可靠投递配置：配合 yaml 里的 publisher-confirm-type / publisher-returns，
 * 把确认与退回回调挂到自动装配的 RabbitTemplate 上。
 *
 * <p>confirm 只负责「感知」消息丢失并留下日志证据（含发布方传入的 CorrelationData，即事件 eventId），
 * 不做自动补偿——真正的可靠投递是本地消息表（outbox），当前消息量与兜底机制下不引入该复杂度。
 */
@Slf4j
@Configuration
public class RabbitReliabilityConfig {

    @Bean
    public RabbitTemplateCustomizer rabbitReliabilityCustomizer() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!Boolean.TRUE.equals(ack)) {
                    log.error("消息未被Broker确认：id={}, cause={}",
                            correlation == null ? null : correlation.getId(), cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                    "消息无法路由到队列：exchange={}, routingKey={}, reply={}, message={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(),
                    new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
        };
    }
}
