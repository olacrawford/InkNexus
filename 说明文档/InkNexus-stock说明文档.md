# 墨枢 InkNexus · Stock 模块说明文档

## 1. 当前职责

`inknexus-stock` 是库存服务模块，负责图书库存查询、下单预占和取消释放。

当前已实现：

- 查询图书可售库存、锁定库存
- 订单下单前原子预占库存，避免并发超卖
- 消费订单支付事件后确认库存，减少预占库存
- 消费取消/超时释放事件后释放订单明细对应的预占库存
- 使用 `stock`、`locked_stock`、`version` 字段维护库存状态与变更计数

## 2. 当前项目结构

启动类：

- [StockApplication.java](D:/workspace_idea/InkNexus/InkNexus/inknexus-stock/src/main/java/com/inknexus/stock/StockApplication.java)

业务代码：

- `controller`：`StockController`
- `service`：`StockService`
- `service.impl`：`StockServiceImpl`
- `mapper`：`StockMapper`，自定义 SQL 见 `src/main/resources/mapper/StockMapper.xml`
- `entity`：`BookStock`
- `dto`：`StockOperationRequest`、`StockOperationItem`
- `vo`：`StockVO`
- `mq`：`OrderStockConsumer`、`RabbitMqConfig`

## 3. 配置说明

服务配置：

- 端口：`8090`
- 服务名：`stock`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`stock.yaml`
- MySQL：`localhost:3306/inknexus`
- RabbitMQ：`localhost:5672`，账号 `admin` / `123456`

数据库连接和 RabbitMQ 配置在 [nacos-config/stock.yaml](D:/workspace_idea/InkNexus/nacos-config/stock.yaml) 中维护。

## 4. 当前接口

接口前缀：`/stock`。通过网关访问时前缀为 `/api/stock`。

### 4.1 GET /stock/hello

健康检查，返回 `inknexus-stock is running`。

### 4.2 GET /stock/{bookId}

查询图书库存。

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "bookId": 1,
    "stock": 98,
    "lockedStock": 2,
    "availableStock": 98
  }
}
```

### 4.3 POST /stock/deduct

预占库存。请求体：

```json
{
  "items": [
    {
      "bookId": 1,
      "quantity": 2
    }
  ]
}
```

库存不足时返回业务错误，同一请求内多本书的预占操作会整体回滚。

### 4.4 POST /stock/release

释放预占库存，请求体格式与 `/stock/deduct` 一致。

### 4.5 POST /stock/confirm

支付成功后确认库存，请求体格式与 `/stock/deduct` 一致。

预占库存时 `stock` 已经减少并计入 `locked_stock`；确认时只减少 `locked_stock`，不会再次扣减可售库存；手工验证时仍可调用 `/stock/release` 恢复，正常链路通过 RabbitMQ 事件触发。

## 5. 数据模型

`BookStock` 映射 `t_book_stock`：

- `bookId`：图书 ID
- `stock`：可售库存
- `lockedStock`：已预占库存
- `version`：变更计数，每次库存更新递增，用于对账或后续扩展
- `createTime`、`updateTime`

## 6. 业务逻辑

- 预占库存使用带条件的原子 `UPDATE`：只有 `stock >= quantity` 时才扣减可售库存并增加锁定库存
- 确认库存使用带条件的原子 `UPDATE`：只有 `locked_stock >= quantity` 时才减少锁定库存
- 释放库存按实际 `locked_stock` 恢复；没有锁定库存时视为已释放，保证取消订单和超时关单可重试
- 库存一致性由这些原子 `UPDATE` 条件保证，`version` 不是乐观锁，仅作为变更计数
- `StockServiceImpl` 的预占、确认和释放都加 `@Transactional`，多商品操作失败时会在库存服务内回滚
- 普通 CRUD 使用 MyBatis-Plus `BaseMapper`，预占/释放等自定义 SQL 放在 `resources/mapper/StockMapper.xml`

## 7. 订单服务接入

订单服务通过 `StockClient` 调用库存服务完成下单预占：

- 直接下单：先预占库存，再创建订单和订单明细
- 购物车下单：校验图书和计算金额后，批量预占库存

支付成功和取消/超时释放不再通过 Feign 调用：

- 订单支付成功发布 `OrderStockEvent`，路由键 `order.paid`
- 订单取消或超时发布 `OrderStockEvent`，路由键 `order.stock.release`
- 库存服务通过 `@RabbitListener` 消费后调用 `confirm` / `release`

## 8. 前端接入

当前前端已接入库存提示：

- [BooksView.vue](D:/workspace_idea/InkNexus/front/src/views/BooksView.vue)：展示可售库存，缺货时禁用购买入口
- [CartView.vue](D:/workspace_idea/InkNexus/front/src/views/CartView.vue)：购物车数量超过库存时提示，并禁止提交结算
- [inknexus.js](D:/workspace_idea/InkNexus/front/src/api/inknexus.js)：`stockApi` 请求封装

## 9. 验证方式

通过网关验证：

```text
GET  http://localhost:8080/api/stock/1
GET  http://localhost:8080/api/stock/hello
POST http://localhost:8080/api/stock/deduct
POST http://localhost:8080/api/stock/release
POST http://localhost:8080/api/stock/confirm
```

库存预占、确认与释放也可以通过订单链路验证：下单后库存 `stock` 减少、`lockedStock` 增加；支付成功后 `lockedStock` 减少；取消订单后恢复。历史待支付订单没有锁定库存时，超时关单会按“已释放”处理，不会一直重试失败。
