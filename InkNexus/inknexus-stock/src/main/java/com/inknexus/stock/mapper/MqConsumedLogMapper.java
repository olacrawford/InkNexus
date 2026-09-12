package com.inknexus.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inknexus.stock.entity.MqConsumedLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MqConsumedLogMapper extends BaseMapper<MqConsumedLog> {

    // INSERT IGNORE 命中 uk_message_id 唯一键时返回 0，作为“消息已消费过”的判定依据
    @Insert("INSERT IGNORE INTO t_mq_consumed_log (message_id, consumer) VALUES (#{messageId}, #{consumer})")
    int insertOnce(@Param("messageId") String messageId, @Param("consumer") String consumer);
}
