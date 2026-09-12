package com.inknexus.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQ 消费去重记录：与库存更新同事务写入，message_id 唯一键保证同一条消息只执行一次。
 */
@Data
@TableName("t_mq_consumed_log")
public class MqConsumedLog {

    private Long id;
    private String messageId;
    private String consumer;
    private LocalDateTime consumeTime;

}
