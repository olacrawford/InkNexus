# 墨枢 InkNexus · Payment 模块说明文档

## 1. 当前职责

`bookmall-payment` 是支付服务模块，当前使用内部模拟支付，不接真实支付宝/微信沙箱。

当前已实现：

- 根据订单生成支付单
- 直接生成已支付结果，模拟支付成功
- 生成支付单后通过 RabbitMQ 发布支付成功事件，订单服务异步更新状态
- 订单服务消费支付事件后再发布订单支付事件，库存服务异步确认库存
- 查询订单对应的支付单
- 支付重复请求做幂等处理

## 2. 当前项目结构

启动类：

- [PaymentApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-payment/src/main/java/com/bookmall/payment/PaymentApplication.java)

业务代码：

- `controller`：`PaymentController`
- `service`：`PaymentService`
- `service.impl`：`PaymentServiceImpl`
- `mapper`：`PaymentMapper`
- `entity`：`Payment`
- `dto`：`PaymentRequest`
- `vo`：`PaymentVO`
- `client`：`OrderClient`
- `client.dto`：`OrderSnapshot`
- `mq`：`PaySuccessPublisher`、`RabbitMqConfig`

## 3. 配置说明

服务配置：

- 端口：`8051`
- 服务名：`payment`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`payment.yaml`
- MySQL：`localhost:3306/bookmall`
- RabbitMQ：`localhost:5672`，账号 `admin` / `123456`

数据库连接和 RabbitMQ 配置在 [nacos-config/payment.yaml](D:/workspace_idea/BookMall/nacos-config/payment.yaml) 中维护。RabbitMQ 配置位于 `spring.rabbitmq`。

## 4. 当前接口

接口前缀：`/payment`。通过网关访问时前缀为 `/api/payment`。

### 4.1 GET /payment/hello

健康检查，返回 `bookmall-payment is running`。

### 4.2 POST /payment/pay

发起内部模拟支付。请求头必须携带 `X-User-Id`。

请求体：

```json
{
  "orderId": 1
}
```

支付成功返回支付单信息。

### 4.3 GET /payment/order/{orderId}

查询某订单对应的支付单，只能查询当前用户的支付单。

## 5. 数据模型

`Payment` 映射 `t_payment`：

- `paymentNo`：支付单号
- `orderId`、`orderNo`：关联订单
- `amount`：支付金额，来自订单服务，不信任前端传入金额
- `payType`：支付渠道，当前固定为 `mock`
- `status`：0 待支付，1 已支付，2 失败
- `payTime`：支付时间

## 6. 业务逻辑

- 支付前通过 `OrderClient` 查询订单，校验订单属于当前用户
- 只有待支付订单可以进入支付流程
- 订单已超过 `expireTime` 时拒绝支付，避免定时任务尚未执行时支付过期订单
- 生成支付单后先保存为已支付，再发布 RabbitMQ 支付成功事件；事件发送失败会回滚本次支付
- 订单服务消费消息后调用 `markPaid`，订单状态和库存确认按 `orderId` 幂等处理
- 订单服务更新状态后发布订单支付事件，库存服务异步消费并确认扣减
- 如果同一订单已存在已支付支付单，直接返回，避免重复支付
- 本地事务失败时支付单和订单状态更新会回滚

## 7. RabbitMQ 消息模型

- 交换机：`bookmall.pay.success.exchange`（Topic）
- 路由键：`pay.success`
- 队列：`bookmall.order.pay.success.queue`
- 消息体：`PaySuccessMessage`，包含 `eventId`、`orderId`、`userId`、`orderNo`、`amount`、`paymentNo`、`payTime`

支付服务使用 `RabbitTemplate` 发布 JSON 字符串，订单服务通过 `@RabbitListener` 消费。

## 8. 订单服务接入

`bookmall-order` 新增接口：

- `PUT /orders/{id}/paid`：手工验证接口，可把待支付订单更新为已支付
- 已支付订单再次调用按幂等成功处理
- 支付服务不再同步调用该接口，正常链路由订单服务消费 `PaySuccessMessage` 后执行

支付服务仍通过服务名 `order` 调用 `GET /orders/{id}` 做支付前订单校验。

## 9. 前端接入

- [OrdersView.vue](D:/workspace_idea/BookMall/front/src/views/OrdersView.vue)：待支付订单显示“立即支付”，已支付订单支持“确认收货”
- [bookmall.js](D:/workspace_idea/BookMall/front/src/api/bookmall.js)：`paymentApi` 请求封装

## 10. 验证方式

通过网关验证：

```text
GET  http://localhost:8080/api/payment/hello
GET  http://localhost:8080/api/payment/order/1
POST http://localhost:8080/api/payment/pay
```

除健康检查外，都需要携带：

```text
Authorization: Bearer xxxxx.yyyyy.zzzzz
```

支付成功后订单状态应从 0 变为 1，同时订单对应图书的 `locked_stock` 应减少；库存确认失败时消息会重试，重复消费不会重复扣减。

RabbitMQ 验证：

- 支付成功后可在 RabbitMQ 管理台看到消息已被消费，队列无堆积
- 可先停止订单服务再支付，消息进入队列保留；订单服务恢复后会自动消费并完成订单状态更新

## 11. 后续可扩展

- 接入支付宝/微信沙箱，用回调异步更新支付状态
- 增加支付中状态
- 增加支付流水对账和退款流程
