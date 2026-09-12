# 墨枢 InkNexus · 数据库设计说明

## 当前基础表

`sql/sql.txt` 是唯一需要维护的建库脚本，包含全部 10 张表与种子数据，新环境直接执行即可完成初始化：

- `t_user`：用户（含 `role` 角色，图书管理接口鉴权用）
- `t_category`：图书分类
- `t_book`：图书（含 `deleted` 软删除标记）
- `t_user_address`：收货地址
- `t_cart_item`：购物车
- `t_book_stock`：图书库存
- `t_order`：订单主表
- `t_order_item`：订单明细
- `t_payment`：支付单
- `t_mq_consumed_log`：MQ 消费去重记录

## 关键设计

### 订单幂等键

`t_order` 上的 `uk_user_request (user_id, client_request_id)` 联合唯一键是下单幂等的根基：可空列 + 联合唯一，传了请求号的请求重复提交时触发 `DuplicateKeyException`，订单服务补偿释放库存并返回已有订单；不传则完全兼容老请求（MySQL 唯一索引不去重 NULL）。

### 库存三态

`t_book_stock` 用 `stock`（可售）与 `locked_stock`（预占）两列表达三态流转：

- 预占：`stock` 减少、`locked_stock` 增加（条件 `stock >= quantity`，防超卖）
- 支付确认：只减少 `locked_stock`，`stock` 保持不变（真实售出）
- 取消/超时释放：`stock` 恢复、`locked_stock` 减少（`LEAST` 钳制，无锁定时按已释放处理）

`version` 只是变更计数，便于对账，一致性由带条件的原子 `UPDATE` 保证。

### 超时关单索引

`t_order` 的 `idx_status_expire_time (status, expire_time)` 服务于超时关单链路：兜底定时任务按「待支付 + 已过期」扫描，延迟消息关单为主、定时扫描兜底。

### 消费幂等

`t_mq_consumed_log` 的 `uk_message_id` 唯一键保证库存确认/释放消息按 `eventId` 只消费一次；去重记录与库存更新写在同一事务，库存操作回滚时记录一并回滚。

## 演进历史

历史上的增量脚本（001～009，从购物车/地址/库存三表到用户角色列、消费去重表）已全部合并进 `sql/sql.txt` 并删除，演进过程可在 Git 提交历史中查看（检索 `sql` 相关提交）。
