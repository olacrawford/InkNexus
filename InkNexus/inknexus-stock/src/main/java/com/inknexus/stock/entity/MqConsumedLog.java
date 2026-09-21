package com.inknexus.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQ 消费去重记录：与库存更新同事务写入，message_id 唯一键保证同一条消息只执行一次。
 * 事务边界是关键不变量：库存操作异常回滚时去重记录一并回滚，消息重投后仍可重试。
 */
@Data
@TableName("t_mq_consumed_log")
public class MqConsumedLog {

    private Long id;
    /**
     * 事件eventId，唯一键 uk_message_id，INSERT IGNORE 冲突即判定为重复消息
     */
    private String messageId;
    /**
     * 消费方业务标识，如 stock-confirm / stock-release，区分同一消息被不同语义消费
     */
    private String consumer;
    private LocalDateTime consumeTime;

}
