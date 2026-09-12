USE inknexus;

-- 004_order_expire_stock_confirm.sql
-- 为订单增加过期时间，支付服务确认库存后仍由 t_book_stock 维护库存：
-- stock 已在下单预占时减少，支付确认只减少 locked_stock。

ALTER TABLE t_order ADD COLUMN expire_time DATETIME DEFAULT NULL COMMENT '订单过期时间' AFTER update_time;

UPDATE t_order
SET expire_time = DATE_ADD(create_time, INTERVAL 30 MINUTE)
WHERE expire_time IS NULL;

ALTER TABLE t_order MODIFY COLUMN expire_time DATETIME NOT NULL COMMENT '订单过期时间';

ALTER TABLE t_order ADD INDEX idx_status_expire_time (status, expire_time);
