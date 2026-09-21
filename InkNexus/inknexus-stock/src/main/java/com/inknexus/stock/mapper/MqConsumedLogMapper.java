package com.inknexus.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.stock.entity.MqConsumedLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MQ 消费去重记录表访问层。
 * 消费幂等的第一道防线：靠数据库唯一键（而非先查后插）抢占消费权，天然并发安全。
 */
@Mapper
public interface MqConsumedLogMapper extends BaseMapper<MqConsumedLog> {

    /**
     * 尝试写入去重记录以抢占消息的消费权。
     * INSERT IGNORE 命中 uk_message_id 唯一键时返回 0，作为“消息已消费过”的判定依据；
     * 调用方必须与业务更新放在同一事务，业务失败回滚时本次抢占一并撤销。
     *
     * @param messageId 事件eventId
     * @param consumer  消费方业务标识（stock-confirm / stock-release）
     * @return 1=首次消费（抢占成功）；0=重复消息
     */
    @Insert("INSERT IGNORE INTO t_mq_consumed_log (message_id, consumer) VALUES (#{messageId}, #{consumer})")
    int insertOnce(@Param("messageId") String messageId, @Param("consumer") String consumer);
}
