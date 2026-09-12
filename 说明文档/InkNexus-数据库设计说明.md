# 墨枢 InkNexus · 数据库设计说明

## 当前基础表

`sql/sql.txt` 保存完整建库脚本，目前包含 9 张表：

- `t_user`：用户
- `t_category`：图书分类
- `t_book`：图书
- `t_user_address`：收货地址
- `t_cart_item`：购物车
- `t_book_stock`：图书库存
- `t_order`：订单主表
- `t_order_item`：订单明细
- `t_payment`：支付单

## 增量脚本规范

新环境可以直接执行 `sql/sql.txt` 完成初始化；已经初始化过的旧环境按顺序执行 `sql/updates/` 下的增量脚本，不重复执行 `sql/sql.txt`。

## 第一阶段新增

脚本：`sql/updates/001_cart_address_stock.sql`

- `t_user_address`：用户收货地址，支持多个地址和默认地址。
- `t_cart_item`：购物车条目，`(user_id, book_id)` 唯一，由 `inknexus-cart` 服务使用，同一本书重复加入时更新数量。
- `t_book_stock`：图书库存表。

执行方式：

```bash
mysql -h127.0.0.1 -uroot -p < sql/updates/001_cart_address_stock.sql
```

## 库存服务接入

脚本：`sql/updates/002_stock_order.sql`

- 不创建新表，只为 `t_book` 中新增但还没有库存行的图书补齐默认库存。
- 脚本可重复执行，适合后续手工插入或管理端新增图书后补库存。

`t_book_stock` 由 `inknexus-stock` 服务使用，下单时更新：

- `stock` 减少、`locked_stock` 增加：预占库存
- `stock` 恢复、`locked_stock` 减少：取消订单释放库存
- `version` 随每次变更递增，作为变更计数，库存一致性由带条件的原子 `UPDATE` 保证

## 支付服务接入

脚本：`sql/updates/003_payment.sql`

新增 `t_payment` 支付单表：

- `payment_no`：支付单号，唯一
- `order_id`、`order_no`：关联订单
- `amount`：支付金额
- `pay_type`：当前固定 `mock`
- `status`：0 待支付，1 已支付，2 失败
- `pay_time`：支付时间

执行方式：

```bash
mysql -h127.0.0.1 -uroot -p < sql/updates/003_payment.sql
```

## 订单超时与库存确认

脚本：`sql/updates/004_order_expire_stock_confirm.sql`

- 给 `t_order` 增加 `expire_time`，保存订单过期时间
- 为已存在的订单按创建时间补齐 `expire_time`，默认 30 分钟
- 增加 `(status, expire_time)` 索引，供超时关单定时任务扫描

订单支付确认后不再改变 `t_book_stock.stock`，因为下单预占时已经扣减：

- 预占：`stock` 减少，`locked_stock` 增加
- 支付确认：`locked_stock` 减少，`stock` 保持不变
- 取消/释放：`stock` 恢复，`locked_stock` 减少；无锁定库存时按已释放处理

执行方式：

```bash
mysql -h127.0.0.1 -uroot -p < sql/updates/004_order_expire_stock_confirm.sql
```

## 查询与并发优化

脚本：`sql/updates/005_optimization.sql`

- 把 `t_order` 的 `idx_user_id` 调整为 `(user_id, create_time)` 复合索引，服务于订单列表按用户和创建时间降序查询

该脚本会先删除旧索引再建新索引；对已有环境执行一次即可，新环境已包含在 `sql/sql.txt` 中。

执行方式：

```bash
mysql -h127.0.0.1 -uroot -p < sql/updates/005_optimization.sql
```
