-- InkNexus 优化升级 006
-- 下单接口幂等：user_id + client_request_id 联合唯一防重
-- 说明：
-- 1. client_request_id 允许 NULL，MySQL 唯一索引不去重 NULL，存量订单与老请求不受影响
-- 2. 重复提交时命中唯一键，订单服务捕获异常后返回已存在订单，并补偿释放本次预占的库存
USE inknexus;

ALTER TABLE t_order
    ADD COLUMN client_request_id VARCHAR(64) DEFAULT NULL COMMENT '客户端请求号，下单幂等防重' AFTER order_no,
    ADD UNIQUE KEY uk_user_request (user_id, client_request_id);
