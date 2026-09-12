-- InkNexus 优化升级 005
-- 1. 订单列表查询使用 (user_id, create_time) 复合索引
USE inknexus;

ALTER TABLE t_order
    DROP INDEX idx_user_id,
    ADD INDEX idx_user_id_create_time (user_id, create_time);