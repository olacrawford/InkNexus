USE inknexus;

-- 002_stock_order.sql：inknexus-stock 接入订单时的增量脚本
-- 仅给 t_book 中还没有库存记录的书补齐默认库存，可重复执行。

INSERT INTO t_book_stock (book_id, stock, locked_stock, version)
SELECT id, 100, 0, 0 FROM t_book
WHERE id NOT IN (SELECT book_id FROM t_book_stock);
