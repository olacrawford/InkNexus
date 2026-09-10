# 墨枢 InkNexus · 改进方案（面试冲刺版）

> 定位：秋招面试冲刺（3~4 天），只做「小改动、低风险、高面试回报」的增量改进。
> 原则：**不影响大体项目**——不删改既有接口契约与表结构语义，只新增可空列、新增队列/端点/配置文件；每项独立提交、独立可回滚，任何一项做不完都不影响其余项与现有功能。
> 范围：仅后端；涉及前端的配合项单独标注，不阻塞后端验收。
> 日期：2026-09-10。更完整的长期演进规划（Outbox 可靠投递、RAG、容器化、支付沙箱、Grafana 等）本版移除，历史版本见 Git（commit 68325bd），后续轮次再规划。

## 一、冲刺总览

| 序 | 任务 | 工时 | 面试考点 |
|---|---|---|---|
| 1 | JWT 密钥治理 | 0.5 天 | 配置外部化、密钥泄露处置 |
| 2 | 下单接口幂等 | 0.5 天 | 唯一索引防重、幂等语义 |
| 3 | 超时关单：轮询 → 延迟消息 | 1 天 | TTL+死信队列、订单闭环 |
| 4 | AI 助手 SSE 流式输出 | 1 天 | 大模型应用工程化 |
| 5 | GitHub Actions CI | 0.5 天 | 工程素养、质量门禁 |
| 6 | Zipkin 链路追踪 | 0.5 天 | 分布式追踪、traceId 贯穿 |
| 7 | 接口压测与性能数据（机动） | 0.5 天 | 性能量化、缓存收益数字 |

必做 1~5 合计约 **3.5 天**；6、7 按剩余时间机动。建议顺序：**1 → 2 → 3 → 4 → 5 →（6 / 7）**。

## 二、分项实施

### 2.1 JWT 密钥治理（0.5 天）

**现状**：`nacos-config/auth.yaml` 与 `gateway.yaml` 各写一份相同明文 secret，且已入 Git 历史——应视为已泄露。

**实施步骤**：

1. 两份 yaml 的值改为环境变量占位符（Spring 运行时解析，`publish.sh` 无需改动）：

```yaml
jwt:
  secret: ${JWT_SECRET:}
  expire-seconds: 86400
```

2. `bookmall-auth` 的 `JwtUtil`、`bookmall-gateway` 的 `AuthGlobalFilter` 启动时快速失败：

```java
@PostConstruct
public void checkSecret() {
    if (secret == null || secret.length() < 32) {
        throw new IllegalStateException("JWT_SECRET 未配置或长度不足 32 位");
    }
}
```

3. 轮换密钥：本地 `export JWT_SECRET=<新生成的 64 位随机串>`；`scripts/dev-macos.sh` 与 README 启动说明同步补充。

**影响面**：只改 2 份 yaml 取值方式 + 2 个校验类；签发/鉴权行为与接口完全不变；不配环境变量时服务拒绝启动是预期防护。
**验收**：仓库与 Nacos 配置中无明文 secret；不设环境变量拒绝启动；登录→网关鉴权链路回归通过。

### 2.2 下单接口幂等（0.5 天）

**现状**：`OrderCreateRequest` 无客户端请求号，`createOrder` 直接插入——连点两次生成两笔订单、重复预占库存。MQ 消费端已有幂等，但用户入口没有。

**实施步骤**：

1. 新增 `sql/updates/006_order_request_id.sql`：

```sql
ALTER TABLE `t_order`
  ADD COLUMN `client_request_id` VARCHAR(64) NULL COMMENT '客户端请求号，下单幂等防重',
  ADD UNIQUE KEY `uk_user_request` (`user_id`, `client_request_id`);
```

按 `user_id + client_request_id` 联合唯一，不同用户相同请求号互不影响；`client_request_id` 允许 NULL，MySQL 唯一索引不去重 NULL，**存量订单与老请求不受任何影响**。

2. `OrderCreateRequest` / `OrderFromCartRequest` 增加可选字段 `clientRequestId`，`insertOrderHead` 透传写入。
3. 插入订单捕获 `DuplicateKeyException` → 按唯一键查回已存在订单直接返回（幂等语义：重复提交返回同一笔订单）。
4. **前端配合项**：下单时生成 UUID 传入（一行代码，可转交前端侧）；后端对老请求保持兼容。

**影响面**：表只加可空列与新索引，不动的列/接口为零；不传 `clientRequestId` 时行为与现在完全一致。
**验收**：同一 `clientRequestId` 重复调用只产生一笔订单、库存只预占一次并返回同一订单号；不同用户请求号相同互不影响。

### 2.3 订单超时关单：定时轮询 → RabbitMQ 延迟消息（1 天）

**现状**：`OrderTimeoutTask` 每 30 秒扫 `status=0 且 expire_time<=now` 的订单，延迟最高 30 秒，且持续空扫。

**设计**：订单超时统一为 30 分钟（`bookmall.order.expire-minutes:30`），用**队列级统一 TTL + 死信**即可，无需每条消息不同 TTL（规避队头阻塞问题）。

**实施步骤**：

1. `bookmall-common/.../mq/BookMallRabbitMq.java` 增加常量：

```java
// 订单超时关闭：下单时发延迟消息，TTL 到期后死信投递给 order 消费
public static final String ORDER_DELAY_QUEUE = "bookmall.order.delay.queue";
public static final String ORDER_CLOSE_EXCHANGE = "bookmall.order.close.exchange";
public static final String ORDER_CLOSE_QUEUE = "bookmall.order.close.queue";
public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";
```

2. `order` 模块 `RabbitMqConfig` 声明延迟队列（死信指向关闭交换机）与关闭队列：

```java
@Bean
public Queue orderDelayQueue() {
    return QueueBuilder.durable(BookMallRabbitMq.ORDER_DELAY_QUEUE)
            .withArgument("x-message-ttl", 30 * 60 * 1000)
            .withArgument("x-dead-letter-exchange", BookMallRabbitMq.ORDER_CLOSE_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", BookMallRabbitMq.ORDER_CLOSE_ROUTING_KEY)
            .build();
}

@Bean
public Queue orderCloseQueue() {
    return QueueBuilder.durable(BookMallRabbitMq.ORDER_CLOSE_QUEUE).build();
}
// ORDER_CLOSE_EXCHANGE 声明 + closeQueue 绑定 order.close
```

3. 下单事务提交后向 `ORDER_DELAY_QUEUE` 直发 `orderId`（发送失败仅告警，兜底任务仍会关单，不影响主流程）。
4. 新增消费者，直接复用已幂等的关单逻辑：

```java
@RabbitListener(queues = BookMallRabbitMq.ORDER_CLOSE_QUEUE)
public void onClose(String orderId) {
    orderService.closeExpiredOrder(Long.valueOf(orderId));
}
```

5. `OrderTimeoutTask` **保留为兜底**，cron 由 `0/30 * * * * ?` 放宽到 `0 */5 * * * ?`。

**影响面**：纯增量（新常量/新队列/新消费者）；现有轮询任务不删除只放宽，MQ 全挂也有兜底关单，主链路零风险。
**验收**：下单后日志可见延迟消息发出；30 分钟整点关单（误差 < 1 秒）；重复投递/支付与关单并发时不误关已支付订单（`closeExpiredOrder` 现有状态判断已覆盖，补一个并发单测）。

### 2.4 AI 助手 SSE 流式输出（1 天）

**现状**：`ChatServiceImpl#chat` 同步返回整段回复，长回答需等待全部生成完。

**实施步骤**：

1. `bookmall-ai` 增加 `OpenAiStreamingChatModel`（复用现有 DashScope compatible-mode base-url 与 key），`AiService` 接口改用 `TokenStream`。
2. 控制器新增流式端点（Servlet 栈，用 `SseEmitter`）：

```java
@GetMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestHeader("X-User-Id") Long userId, @RequestParam String message) {
    SseEmitter emitter = new SseEmitter(60_000L);
    chatService.streamChat(userId, message,
            delta -> sendSafely(emitter, delta),
            emitter::complete,
            emitter::completeWithError);
    return emitter;
}
```

3. 网关注意点：Spring Cloud Gateway 是 WebFlux 栈，可直接透传 SSE；确认网关 `response-timeout` 大于流式时长。
4. Redis 会话记忆写入时机改为完整回复拼装完成后，保持现有 memory 结构不变。
5. **前端配合项**：`AiChatView` 改用 `@microsoft/fetch-event-source` 逐字渲染，由前端侧排期；后端验收用 `curl -N` 直接观察逐字输出，不阻塞本项。

**影响面**：只新增一个流式端点，原同步端点与前端现有调用完全不动；新端点出问题不影响旧功能。
**验收**：`curl -N` 调流式端点首字 1~2 秒内出现、逐字输出、连接正常收尾；多轮会话记忆不丢。

### 2.5 GitHub Actions CI（0.5 天）

新增 `.github/workflows/ci.yml`（现有单测为纯 Mockito，无需中间件即可跑；gateway 的 `macos-arm64` profile 仅在 mac 激活，ubuntu 不受影响）：

```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - run: mvn -f BookMall/pom.xml -q test

  frontend:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: front } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: npm, cache-dependency-path: front/package-lock.json }
      - run: npm ci
      - run: npm run build
```

（frontend job 保留用于守护前端产出，不属于本方案工作范围。）

**影响面**：只新增一个 workflow 文件，不碰任何代码。
**验收**：PR 自动跑后端测试与前端构建，红灯可阻断合并。

### 2.6 Zipkin 链路追踪（0.5 天）

1. 各业务模块（建议先 order/payment/stock，后铺开）加入：

```xml
<dependency>io.micrometer:micrometer-tracing-bridge-brave</dependency>
<dependency>io.zipkin.reporter2:zipkin-reporter-brave</dependency>
```

2. Nacos 各服务配置（或抽公共 data-id）：

```yaml
management:
  tracing:
    sampling.probability: 1.0        # 演示环境全采样
  zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

3. `docker-compose.infra.yml` 追加 zipkin 服务（`openzipkin/zipkin`，9411）。
4. Feign 已默认透传 trace 上下文；验证一次「下单→预占」在 Zipkin UI 里的跨服务调用树。

**影响面**：只加依赖与配置；reporter 异步上报，Zipkin 挂掉不影响任何业务请求。
**验收**：Zipkin 中能看到一次请求贯穿 gateway→order→stock 的完整链路与耗时。

### 2.7 接口压测与性能数据（0.5 天，机动）

**目的**：面试中「缓存开启后接口 P99 从 X ms 降到 Y ms」远比「我用了 Redis」有说服力。

**实施步骤**：

1. JMeter 简单线程组（100 并发 × 60s）：图书分页查询（Redis 缓存开/关对比）、下单链路（观察 Sentinel 流控触发）。
2. 产出简短压测记录（`说明文档/BookMall-压测记录.md`）：QPS、RT 均值/P99、错误率、缓存开关对比与限流生效截图。
3. 写清前置条件（种子数据量、预热次数），保证数字可复现。

**影响面**：零代码改动，纯只读压测。
**验收**：可写进简历的具体性能数字至少 2 组，且可复现。

## 三、风险与注意事项

- **密钥只进环境变量**：`JWT_SECRET`、`DASHSCOPE_API_KEY` 一律不入库；`.env` 加入 `.gitignore`。
- **Nacos 占位符** `${JWT_SECRET:}` 依赖启动环境注入，`scripts/dev-macos.sh` 与 README 必须同步，否则起不来服务。
- **延迟消息 TTL 队列**仅适用统一超时时长；未来若支持用户自选超时，需换 RabbitMQ 延迟插件或回归定时任务兜底（本轮兜底任务保留，已覆盖）。
- **幂等唯一索引与历史数据**：`client_request_id` 允许 NULL（MySQL 唯一索引不去重 NULL），存量订单无需刷数据。
- **SSE 经网关透传**：网关 response-timeout 必须大于流式时长；Servlet 容器注意异步请求超时配置。
- 每项落地后按仓库约定同步 `README.md`、`说明文档/`、`sql/`、`nacos-config/` 与 `AGENTS.md`。
