# 墨枢 InkNexus · 增强项实施说明

## 1. 当前已落地增强项

当前已实现以下增强能力：

- `bookmall-book` 图书列表、分页、详情、分类使用 Spring Cache + Redis
- `bookmall-book` 图书查询接口使用 Sentinel QPS 限流
- `bookmall-payment` / `bookmall-order` 使用 RabbitMQ 做支付成功事件的最终一致性补偿
- `bookmall-order` / `bookmall-stock` / `bookmall-payment` 补充核心服务单元测试
- `bookmall-cart` 使用数据库唯一键原子更新，避免并发加购重复插入
- `cart` / `order` / `payment` 配置 OpenFeign 默认连接和读取超时
- `bookmall-order` 超时关单分批处理，`t_order` 使用 `(user_id, create_time)` 复合索引
- `bookmall-ai` 基于 LangChain4j + DashScope 的只读 AI 助手，会话记忆存 Redis

## 2. Redis 缓存

### 2.1 实现方式

- [RedisConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/RedisConfig.java) 提供基于 Redis 的 `CacheManager`
- [BookApplication.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/BookApplication.java) 开启 `@EnableCaching`
- [BookServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/service/impl/BookServiceImpl.java) 在 `getBookById()` 上使用 `@Cacheable(cacheNames = "book")`
- [CategoryServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/service/impl/CategoryServiceImpl.java) 在分类查询上使用 `@Cacheable(cacheNames = "category")`
- 缓存统一 30 分钟过期，查询结果为 `null` 时不写入缓存
- 新增、修改、删除图书使用 `@Caching` 清理 `book` 和 `books` 两个缓存空间

### 2.2 当前缓存范围

- 缓存对象：图书详情、图书列表、图书分页、分类列表
- Redis 缓存键：`book::<id>`
- `books::...`
- `category::...`

## 3. Sentinel 限流

### 3.1 实现方式

- [SentinelConfig.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/config/SentinelConfig.java) 使用代码定义流控规则
- [BookServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/service/impl/BookServiceImpl.java) 在 `listBooks()`、`pageBooks()`、`getBookById()` 上使用 `@SentinelResource`
- [CategoryServiceImpl.java](D:/workspace_idea/BookMall/BookMall/bookmall-book/src/main/java/com/bookmall/book/service/impl/CategoryServiceImpl.java) 在 `listCategories()` 上使用 `@SentinelResource`

### 3.2 当前规则

- 流控模式：QPS
- `listBooks`：50 QPS
- `pageBooks`：80 QPS
- `getBookById`：120 QPS
- `listCategories`：80 QPS
- 超限响应：各资源返回对应的 429 友好提示

## 4. RabbitMQ 最终一致性

### 4.1 实现链路

- `PaymentServiceImpl` 支付成功后调用 `PaySuccessPublisher`
- `PaySuccessPublisher` 使用 `RabbitTemplate` 把 `PaySuccessMessage` 发布到 `bookmall.pay.success.exchange`
- `bookmall-order` 的 `PaySuccessConsumer` 使用 `@RabbitListener` 订阅 `bookmall.order.pay.success.queue`
- 消费端调用 `markPaid`，订单状态和库存确认按 `orderId` 幂等处理

订单消费 `PaySuccessMessage` 后调用 `markPaid`，并发布 `OrderStockEvent` 给库存服务：

- 路由键 `order.paid`：库存服务确认库存
- 路由键 `order.stock.release`：库存服务释放库存
- 下单前的库存预占仍使用同步 Feign，因为创建订单时需要立即确认库存是否充足

消息重复消费通过支付订单幂等和库存重复确认判断保证不会重复扣减库存。

AI 只读助手（LangChain4j + DashScope）的实现与验证见 [BookMall-ai-assistant说明文档.md](BookMall-ai-assistant说明文档.md)，此处不再重复。

## 5. 核心服务单元测试

全部 9 个后端模块（auth/book/cart/stock/order/payment/gateway/ai/common 场景见各模块文档「验证与测试」节）均有 JUnit 5 + Mockito 单元测试，不依赖中间件：

```bash
mvn -f BookMall/pom.xml -q test
```

## 6. 验证方式

Redis 缓存验证：

```text
GET http://localhost:8080/api/books
GET http://localhost:8080/api/books/1
GET "http://localhost:8080/api/books/page?pageNum=1&pageSize=10"
GET http://localhost:8080/api/books/categories
```

Redis 中可见 `book*`、`books*`、`category*` 缓存键。

Sentinel 验证：

```bash
for i in $(seq 1 100); do curl -s -o /dev/null "http://localhost:8080/api/books/page?pageNum=1&pageSize=10"; done
```

分页接口超过 80 QPS 后返回 429。

## 7. 当前状态

本文档只描述当前已实现并验证的增强能力。
