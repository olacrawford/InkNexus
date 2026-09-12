-- 009: MQ 消费去重记录表，库存确认/释放消息按 eventId 幂等消费（消息可靠性专项）
-- 适用：已经初始化过的存量环境；全新环境直接运行 sql/sql.txt 初始化即可（已包含该表）。
-- 该表在 stock 库存服务中与库存更新同事务写入：同一 eventId 只会执行一次，
-- 库存操作回滚时记录一并回滚，消息重投后仍可重试。

CREATE TABLE IF NOT EXISTS t_mq_consumed_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息 eventId（发布时生成，重投不变）',
    consumer VARCHAR(50) NOT NULL COMMENT '消费方：stock-confirm / stock-release',
    consume_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    UNIQUE KEY uk_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费去重记录';

-- 说明：记录会随时间增长，可按 consume_time 定期归档清理（如保留 30 天）；
-- 清理只影响“极老消息重投”这一低概率场景的防重能力，不影响业务正确性。
