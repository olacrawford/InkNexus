# 墨枢 InkNexus · Order 模块说明文档

## 1. 当前职责

`bookmall-order` 是订单服务模块，负责用户直接下单、购物车下单和订单管理。

当前已实现：

- 根据 `X-User-Id` 创建订单，不信任前端传入的用户 ID
- 通过 OpenFeign 调用图书服务，快照图书标题、价格
- 通过 OpenFeign 读取购物车已选条目，一次创建多条订单明细
- 下单前通过 `StockClient` 预占库存，本地落库失败时补偿释放
- 通过 RabbitMQ 消费支付成功事件，把订单更新为已支付
- 订单支付成功后再通过 RabbitMQ 发布订单支付事件，库存服务确认库存
- 通过定时任务关闭超时未支付订单，并释放预占库存
- 取消订单和超时关单时发布库存释放事件，库存服务异步释放预占库存
- 创建订单主表和订单明细
- 查询当前用户订单列表
- 查询订单详情（只能查看自己的订单）
- 取消待支付订单（只能取消自己的订单），取消时释放预占库存
- 提供已支付接口，保留手工验证；正常链路由 RabbitMQ 支付成功事件触发
- 确认收货：已支付订单可更新为已完成，只能操作自己的订单

## 2. 当前项目结构

启动类：

- [OrderApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-order/src/main/java/com/bookmall/order/OrderApplication.java)

业务代码：

- `controller`：`OrderController`
- `service`：`OrderService`
- `service.impl`：`OrderServiceImpl`
- `mapper`：`OrderMapper`、`OrderItemMapper`
- `entity`：`Order`、`OrderItem`
- `dto`：`OrderCreateRequest`、`OrderFromCartRequest`
- `vo`：`OrderVO`、`OrderDetailVO`
- `client`：`BookClient`、`CartClient`、`StockClient`（当前只用于下单预占）
- `client.dto`：`BookSnapshot`、`CartItemSnapshot`、`StockOperationItem`、`StockOperationRequest`
- `task`：`OrderTimeoutTask`
- `mq`：`PaySuccessConsumer`、`OrderEventPublisher`、`RabbitMqConfig`

## 3. 配置说明

服务配置：

- 端口：`8050`
- 服务名：`order`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`order.yaml`
- MySQL：`localhost:3306/bookmall`
- RabbitMQ：`localhost:5672`，账号 `admin` / `123456`
- 订单过期时间：`bookmall.order.expire-minutes`，默认 `30` 分钟
- 超时任务频率：`bookmall.order.close-cron`，默认每 `30` 秒执行一次
- 超时任务单轮处理量：`bookmall.order.close-batch-size`，默认 `500` 条
- 默认 OpenFeign 连接超时 3 秒、读取超时 5 秒

数据库连接、RabbitMQ 和 OpenFeign 配置在 [nacos-config/order.yaml](D:/workspace_idea/BookMall/nacos-config/order.yaml) 中维护，RabbitMQ 配置位于 `spring.rabbitmq`。

## 4. 当前接口

接口前缀：`/orders`

### 4.1 GET /orders/hello

健康检查，返回 `bookmall-order is running`。

### 4.2 POST /orders

创建订单。请求头必须携带 `X-User-Id`，该值由网关解析 JWT 后透传。

请求体：

```json
{
  "bookId": 1,
  "quantity": 2,
  "receiverName": "张三",
  "receiverPhone": "13800000000",
  "receiverAddress": "上海市 浦东新区 测试路 1 号",
  "clientRequestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`clientRequestId` 可选（最长 64 位）。传入后以 `(user_id, client_request_id)` 唯一索引做下单幂等：同一用户重复提交返回同一笔订单，并自动补偿释放重复预占的库存；不传则行为与旧版完全一致。

### 4.3 POST /orders/from-cart

从购物车下单。请求头必须携带 `X-User-Id`，订单服务会读取购物车中已选中的商品。

请求体：

```json
{
  "receiverName": "张三",
  "receiverPhone": "13800000000",
  "receiverAddress": "上海市 浦东新区 测试路 1 号",
  "clientRequestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`clientRequestId` 含义与 `POST /orders` 相同。

### 4.4 GET /orders

查询当前用户订单列表。

### 4.5 GET /orders/{id}

查询订单详情，只能查询当前用户的订单。

### 4.6 PUT /orders/{id}/cancel

取消订单，只允许取消状态为待支付的订单。

### 4.7 PUT /orders/{id}/paid

订单服务消费 `PaySuccessMessage` 后调用 `markPaid`：把待支付订单更新为已支付，并发布订单支付事件由库存服务确认库存；已支付订单重复调用按成功处理。

### 4.8 PUT /orders/{id}/complete

确认收货，只允许当前用户把已支付订单更新为已完成；已完成订单重复调用按成功处理。

### 4.9 定时任务：关闭超时订单

`OrderTimeoutTask` 每 30 秒扫描一次，只关闭已超过 `expire_time` 且仍为待支付的订单，每轮最多处理 `close-batch-size` 条；关闭成功后会释放该订单预占的库存。库存释放失败时订单状态回滚，下一轮任务会重试。

存量环境升级后会为历史订单补齐 `expire_time`，因此很久以前创建的待支付订单可能在下一次扫描时被自动取消；如果历史订单没有锁定库存，释放操作会按“已释放”处理，不会卡住任务。

## 5. 数据模型

`Order` 映射 `t_order`：

- `orderNo`、`userId`、`totalAmount`
- `status`（0 待支付，1 已支付，2 已取消，3 已完成）
- `receiverName`、`receiverPhone`、`receiverAddress`
- `createTime`、`expireTime`、`updateTime`

`OrderItem` 映射 `t_order_item`：

- `orderId`、`bookId`、`bookTitle`
- `bookPrice`、`quantity`、`subtotal`
- `createTime`

订单明细保存下单时的标题和价格快照，后续修改图书价格不会影响历史订单。

## 6. 服务间调用

- [BookClient.java](D:/workspace_idea/BookMall/BookMall/bookmall-order/src/main/java/com/bookmall/order/client/BookClient.java) 使用 OpenFeign
- 服务名：`book`
- 调用接口：`GET /books/{id}`
- 返回体转换为订单模块自己的 `BookSnapshot`

- [CartClient.java](D:/workspace_idea/BookMall/BookMall/bookmall-order/src/main/java/com/bookmall/order/client/CartClient.java) 使用 OpenFeign
- 服务名：`cart`
- 调用接口：`GET /cart/selected`
- 返回体转换为订单模块自己的 `CartItemSnapshot`

- [StockClient.java](D:/workspace_idea/BookMall/BookMall/bookmall-order/src/main/java/com/bookmall/order/client/StockClient.java) 使用 OpenFeign
- 服务名：`stock`
- 调用接口：`POST /stock/deduct`
- 只在创建订单时同步预占库存，保证下单前能确认可售库存充足

订单服务也向支付服务提供内部调用接口：

- `GET /orders/{id}`：校验订单归属并返回订单快照
- `PUT /orders/{id}/paid`：保留手工验证入口，正常支付链路不再通过它更新订单

前端在图书下单和购物车结算时可以选择已保存收货地址自动带入；地址最终仍由订单请求直接携带，尚未拆分为独立地址微服务。下单成功后，前端会清理已下单的购物车条目。

## 7. RabbitMQ 异步补偿链路

- 支付服务发布 `PaySuccessMessage` 到 `bookmall.pay.success.exchange`，路由键 `pay.success`
- 队列：`bookmall.order.pay.success.queue`
- `PaySuccessConsumer` 使用 `@RabbitListener` 订阅，解析消息后调用 `markPaid`
- `markPaid` 按订单状态做幂等：已支付或已取消的消息不会重复确认库存
- 订单支付成功后，`OrderEventPublisher` 发布 `OrderStockEvent` 到 `bookmall.order.stock.exchange`
- 库存服务消费确认或释放事件，异步处理 `locked_stock`

## 8. 验证方式

通过网关验证：

```text
GET http://localhost:8080/api/orders/hello
POST http://localhost:8080/api/orders
POST http://localhost:8080/api/orders/from-cart
GET http://localhost:8080/api/orders
GET http://localhost:8080/api/orders/1
PUT http://localhost:8080/api/orders/1/cancel
PUT http://localhost:8080/api/orders/1/paid
PUT http://localhost:8080/api/orders/1/complete
```

除 `GET /orders/hello` 外，其他接口都需要在请求头携带：

```text
Authorization: Bearer xxxxx.yyyyy.zzzzz
```

网关校验 JWT 后会写入 `X-User-Id`。

RabbitMQ 验证：

- 启动 `rabbitmq` 容器后启动订单服务，日志出现监听 `bookmall.order.pay.success.queue`
- 支付成功后，RabbitMQ 管理台该队列应无持续堆积消息
- 可手动向该队列发送一条合法 `PaySuccessMessage` JSON，验证订单会被幂等更新
