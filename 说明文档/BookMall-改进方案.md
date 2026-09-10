# 墨枢 InkNexus · 改进方案

> 定位：从「功能跑通的演示项目」升级为「具备生产思维的架构展示项目」。
> 口径：本文档是**演进规划**，与「当前已实现」口径的模块文档区分；每项落地后在对应模块文档同步补充。
> 范围：**仅后端与工程化**。前端现代化（Pinia / UI 库 / 前端测试）不在本方案内，由前端侧自行规划；涉及前端的配合项在文中单独标注。
> 日期：2026-09-10；修订：2026-09-10 评审后聚焦后端（前端现代化移出，支付宝沙箱 / Grafana / 集成测试降为选做，新增下单幂等与压测数据）。

## 一、现状评估

**已具备**：六服务 + 网关 + AI 助手的完整拆分；Nacos 注册与配置；网关统一 JWT 鉴权与 `X-User-Id` 透传；Redis 缓存与 Sentinel 流控；RabbitMQ 支付成功/库存确认释放事件（消费端按订单状态幂等）；库存预占→支付确认→超时释放闭环；9 个模块 Mockito 单元测试；Knife4j 文档；完善的中文文档体系。

**主要短板**：

| # | 短板 | 现状证据 |
|---|---|---|
| 1 | JWT 密钥硬编码且已提交 Git，auth/gateway 两处重复 | `nacos-config/auth.yaml`、`nacos-config/gateway.yaml` 均为 `bookmall-jwt-secret-key-2026-safe` |
| 2 | 订单超时靠 30 秒轮询，延迟不精确、空扫 DB | `bookmall-order/.../task/OrderTimeoutTask.java`（cron `0/30 * * * * ?`） |
| 3 | 支付为纯 mock，链路缺真实支付语义 | `PaymentServiceImpl#createPayment` 落单即 `status=1`、`payType="mock"` |
| 4 | MQ 生产端「事务内直接发」，非真正可靠 | `PaymentServiceImpl#pay` 事务内 `publishPaySuccess`；未开启 publisher confirm |
| 5 | 下单接口无防重，连点生成重复订单并重复预占库存 | `OrderCreateRequest` 无客户端请求号；`createOrder` 直接插入 |
| 6 | 无 CI / 无后端镜像 / 无集成测试 | 仓库无 `.github/workflows`；仅 `front/` 有 Dockerfile |
| 7 | 无链路追踪与指标监控 | 各模块无 micrometer-tracing / actuator 暴露配置 |
| 8 | AI 助手同步阻塞返回，检索仅靠 Tool 查库 | `ChatServiceImpl#chat` 同步返回；无流式、无向量检索 |
| 9 | 前端无状态管理与 UI 库，token 存 localStorage | `front/package.json` 仅 vue/vue-router/axios（**范围外**，前端侧规划） |

## 二、范围与品牌记录

- **品牌**：已选定 **墨枢 · InkNexus**（2026-09-10 落地）。仅改品牌层文案，**不动** Git 仓库名、Maven `groupId/artifactId`、包名 `com.bookmall`、端口与 Nacos 服务名、数据库名与本地存储 key，避免无意义的大重构；compose 项目名保持 `bookmall-infra`（变更会与运行中的旧容器并存抢占端口）。
- **范围**：本方案只覆盖后端与工程化。前端现代化由前端侧负责，本方案仅在必要处标注「前端配合项」。

## 三、P0 安全与正确性

### 3.1 JWT 密钥治理

**现状**：`nacos-config/auth.yaml` 与 `gateway.yaml` 各写一份相同明文 secret，且已入 Git 历史——应视为已泄露。

**实施步骤**：

1. 两份 yaml 的值改为环境变量占位符（Spring 运行时从环境变量解析，`publish.sh` 无需改动）：

```yaml
jwt:
  secret: ${JWT_SECRET:}
  expire-seconds: 86400
```

2. `bookmall-auth` 的 `JwtUtil`、`bookmall-gateway` 的 `AuthGlobalFilter` 启动时快速失败（新增一个校验类即可）：

```java
@PostConstruct
public void checkSecret() {
    if (secret == null || secret.length() < 32) {
        throw new IllegalStateException("JWT_SECRET 未配置或长度不足 32 位");
    }
}
```

3. 轮换密钥：本地 `export JWT_SECRET=<新生成的 64 位随机串>`；`scripts/dev-macos.sh` 与 README 启动说明同步补充该环境变量。
4. （可选）进一步把 `jwt` 段抽成共享 data-id（如 `bookmall-common.yaml`），auth 与 gateway 通过 `spring.config.import` 共同引入，消除两处配置漂移。

**验收**：仓库与 Nacos 配置中不再出现明文 secret；不设环境变量时服务拒绝启动；登录→网关鉴权链路回归通过。
**工时**：0.5 天。

### 3.2 订单超时关单：定时轮询 → RabbitMQ 延迟消息

**现状**：`OrderTimeoutTask` 每 30 秒扫 `status=0 且 expire_time<=now` 的订单，延迟最高 30 秒，且持续空扫。

**设计**：项目订单超时统一为 30 分钟（`bookmall.order.expire-minutes:30`），因此用**队列级统一 TTL + 死信**即可，无需每条消息不同 TTL（规避队头阻塞问题）。

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

5. `OrderTimeoutTask` 降级为兜底：cron 由 `0/30 * * * * ?` 放宽到 `0 */5 * * * ?`。

**验收**：下单后日志可见延迟消息发出；30 分钟整点关单（误差 < 1 秒）；重复投递/支付与关单并发时不误关已支付订单（`closeExpiredOrder` 现有状态判断已覆盖，补一个并发单测）。
**工时**：1 天。

### 3.3 本地消息表（Outbox）保证事件可靠发布

**现状**：`PaymentServiceImpl` 在事务内 `rabbitTemplate.convertAndSend`，注释依赖「发送失败回滚」——但 convertAndSend 是异步投递，网络抖动时可能「事务已提交而消息未送达」，且未开启 publisher confirm。

**实施步骤**：

1. 新增 `sql/updates/006_payment_outbox.sql`：

```sql
CREATE TABLE IF NOT EXISTS `payment_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `message_type` VARCHAR(32) NOT NULL DEFAULT 'PAY_SUCCESS',
  `payload` VARCHAR(4096) NOT NULL COMMENT 'PaySuccessMessage JSON',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待发送 1=已发送',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NULL,
  `create_time` DATETIME NOT NULL,
  `update_time` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付成功事件本地消息表';
```

2. `PaymentServiceImpl#pay` 事务内只写 `payment` + `outbox`（同库原子），不再直接发 MQ。
3. 新增 `OutboxRelay`：`@Scheduled(fixedDelay=1000)` 扫 `status=0` 记录发送；配合 publisher confirm 回调置 `status=1`：

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
```

4. 失败重试：`retry_count+1`、`next_retry_time` 指数退避；超过 5 次打 ERROR 告警日志。
5. 同一模式后续推广到 `OrderEventPublisher`（订单→库存事件）。

**验收**：人为停掉 RabbitMQ 后完成支付，MQ 恢复后消息自动补发，订单最终一致；消息不丢不重（重由消费端幂等兜底）。
**工时**：1.5 天。

### 3.4 下单接口幂等（新增）

**现状**：`OrderCreateRequest` 无客户端请求号，`createOrder` 直接插入——连点两次生成两笔订单、重复预占库存。MQ 消费端已有幂等，但用户入口没有。

**实施步骤**：

1. 新增 `sql/updates/007_order_request_id.sql`：

```sql
ALTER TABLE `t_order`
  ADD COLUMN `client_request_id` VARCHAR(64) NULL COMMENT '客户端请求号，下单幂等防重',
  ADD UNIQUE KEY `uk_user_request` (`user_id`, `client_request_id`);
```

唯一索引按 `user_id + client_request_id` 联合建，不同用户相同请求号互不影响；`client_request_id` 允许 NULL，MySQL 唯一索引不去重 NULL，历史订单不受影响。

2. `OrderCreateRequest` / `OrderFromCartRequest` 增加可选字段 `clientRequestId`，`insertOrderHead` 透传写入。
3. 插入订单捕获 `DuplicateKeyException` → 按唯一键查回已存在订单直接返回（幂等语义：同一请求重复提交，返回同一笔订单）。
4. **前端配合项**：下单时生成 UUID 作为 `clientRequestId` 传入（前端一行代码，可转交前端侧）；后端对老请求（无该字段）保持兼容。

**验收**：同一 `clientRequestId` 重复调用只产生一笔订单、库存只预占一次并返回同一订单号；不同用户请求号相同互不影响。
**工时**：0.5 天。

## 四、P1 工程化

### 4.1 GitHub Actions CI

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

（frontend job 保留用于守护前端同学的产出，不属于本方案工作范围。）

**验收**：PR 自动跑后端测试与前端构建，红灯可阻断合并。
**工时**：0.5 天。

### 4.2 后端 Dockerfile + 全栈编排

1. 根目录放通用多阶段 `BookMall/Dockerfile`（`ARG MODULE` 区分服务，利用 Maven 分层缓存）：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY BookMall/ /src/
RUN mvn -q -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:17-jre
ARG MODULE
COPY --from=build /src/${MODULE}/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

2. 新增 `docker-compose.app.yml`：依赖既有 `bookmall-infra` 网络，起 auth/book/cart/stock/order/payment/gateway/ai 八个服务（镜像 build 参数传各自模块名），`JWT_SECRET`、`DASHSCOPE_API_KEY` 从 `.env` 注入。
3. 注意：容器内服务依赖 Nacos 配置，其中 DB/Redis 地址需从 `localhost` 调整为容器服务名——在 compose 里用 `SPRING_CONFIG_IMPORT` + 专用 `nacos-config/*.yaml`（`docker` profile）或直接环境变量覆盖，方案落地时二选一并写入部署文档。

**验收**：`docker compose up` 一条命令拉起全栈，前端可完整走通下单链路。
**工时**：1.5 天。

### 4.3 Testcontainers 集成测试（缩为单条核心链路）

> 修订说明：原方案附带 JaCoCo 行覆盖率红线。覆盖率门槛对演示项目是负资产（数字低反而在面试中减分），已删除；只保留一条覆盖最核心链路的 IT。

选依赖最重的库存链路做一条 IT（放 `bookmall-stock` 或 `bookmall-order`）：

```java
@SpringBootTest
@Testcontainers
class StockReserveReleaseIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:8.8").withExposedPorts(6379);
    @Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3.5-management");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) { /* datasource/redis/rabbit 映射 */ }

    @Test
    void reserve_thenCloseOrder_shouldReleaseStock() { /* 预占 → 关单 → 校验库存回补 */ }
}
```

跨服务 Feign 调用用 WireMock 桩掉，保持 IT 单服务内闭环。CI 中 `mvn verify` 生效（IT 默认绑定 failsafe，本地无 Docker 时 `-DskipITs` 跳过）。

**验收**：`mvn -f BookMall/pom.xml verify` 在本地与 CI 均绿，核心链路在真实 MySQL/Redis/RabbitMQ 上回归通过。
**工时**：1 天。

### 4.4 接口压测与性能数据（新增）

**目的**：面试中「缓存开启后接口 P99 从 X ms 降到 Y ms」远比「我用了 Redis」有说服力，需要拿到具体数字。

**实施步骤**：

1. JMeter 简单线程组（100 并发 × 60s 即可）压两组接口：图书分页查询（Redis 缓存开/关对比）、下单链路（观察 Sentinel 流控规则触发）。
2. 产出一份简短压测记录（`说明文档/BookMall-压测记录.md` 或 README 表格）：QPS、RT 均值/P99、错误率，缓存开关对比与 Sentinel 限流生效截图。
3. 压测前置条件写清楚（种子数据量、预热次数），保证数字可复现。

**验收**：简历/面试可引用的具体性能数字至少 2 组，且可复现。
**工时**：0.5 天。

## 五、P1~P2 AI 与缓存亮点

### 5.1 AI 助手 SSE 流式输出（后端部分）

**现状**：`ChatServiceImpl#chat` 同步返回整段回复，长回答体验差。

**实施步骤**：

1. `bookmall-ai` 增加 `OpenAiStreamingChatModel`（复用现有 DashScope compatible-mode base-url 与 key），`AiService` 接口改用 `TokenStream`。
2. 控制器新增流式端点（当前是 Servlet 栈，用 `SseEmitter`）：

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

3. 网关注意点：Spring Cloud Gateway 是 WebFlux 栈，可直接透传 SSE；确认网关 `response-timeout` 大于流式时长，避免长回答被网关掐断。
4. Redis 会话记忆写入时机改为完整回复拼装完成后，保持现有 memory 结构不变。
5. **前端配合项**：`AiChatView` 改用 `@microsoft/fetch-event-source`（原生 `EventSource` 不支持自定义 `Authorization` 头）逐字渲染，由前端侧排期；后端验收用 `curl -N` 直接观察逐字输出即可，不阻塞本项。

**验收**：`curl -N` 调流式端点首字 1~2 秒内出现、逐字输出、连接正常收尾；多轮会话记忆不丢。
**工时**：1 天（纯后端）。

### 5.2 语义搜书（RAG）

**实施步骤**：

1. 嵌入模型：`langchain4j-embeddings-bge-small-zh-v15`（本地运行，零 API 成本，中文效果好）；书目为千级以下，向量存储用 `InMemoryEmbeddingStore` + 启动时从 book 表全量构建并持久化快照，规模化再换 Milvus（面试话术：数据量决定选型，千级内存索引足够，演进路径明确）。
2. 索引刷新：服务启动全量构建 + 每日定时增量刷新；管理端点 `POST /api/ai/admin/reindex` 可选保留，但需校验管理令牌（网关层无角色模型，不能裸暴露给普通登录用户）。
3. 新增 Tool 并注册进现有 `AiAssistant`：

```java
@Component
public class SearchBookBySemanticTool {
    @Tool("根据用户描述语义搜索图书，返回书名/作者/价格/简介")
    public List<BookBrief> search(String query, int topK) { /* embedding → 相似度检索 */ }
}
```

4. 效果验证：「有没有类似《三体》的硬科幻」能返回语义近邻书目。

**验收**：自然语言描述可召回相关图书；重建索引后新书可被检索。
**工时**：2 天。

### 5.3 缓存一致性强化

1. 核对 `BookServiceImpl`/`CategoryServiceImpl` 的写路径：更新/删除必须按 key 失效缓存，空值缓存 TTL 要短于正常缓存（防穿透）。
2. 引入**延迟双删**：删缓存 → 更新 DB → 延迟 500ms 再删一次（用 `CompletableFuture.delayedExecutor` 或 MQ 延迟消息），覆盖「删后读旧值回填」窗口。
3. 进阶亮点：接 Canal 订阅 binlog 失效缓存（可只写设计文档作为演进项）。

**验收**：并发「读-更新」测试下，缓存最终一致；文档补一致性方案说明。
**工时**：1 天。

## 六、P2 可观测性：链路追踪

### 6.1 Micrometer Tracing + Zipkin

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

**验收**：Zipkin 中能看到一次请求贯穿 gateway→order→stock 的完整链路与耗时。
**工时**：0.5 天。

（指标监控 Prometheus + Grafana 移入「选做池」7.2。）

## 七、选做池（时间富余再做）

### 7.1 支付宝沙箱支付（简化版）

> 修订说明：原列 P0，但依赖内网穿透 + 沙箱账号 + 密钥工具，任何一环出问题演示就翻车，且面试考点集中在回调验签与幂等，不在「真实穿透」。降为选做，按简化版实施。

**实施步骤**：

1. `bookmall-payment/pom.xml` 引入 `com.alipay.sdk:alipay-sdk-java`（4.38+）；密钥（`ALIPAY_APP_ID`/`ALIPAY_PRIVATE_KEY`/`ALIPAY_PUBLIC_KEY`）只放环境变量。
2. 支付流程拆为两段，保留完整面试考点：
   - `POST /api/payment/alipay/{orderId}`：校验订单（复用现有 `getPayableOrder`）→ 落 `status=0` 待支付支付单 → 调 `alipay.trade.page.pay` 返回支付表单/跳转 URL；
   - `POST /api/payment/alipay/notify`：**验签** → 校验金额与订单号 → 幂等更新支付单为已支付（按 `payment.status` 判断，复用 `publishPaySuccess` 发布事件）→ 返回 `"success"`。
3. 简化点：`notify-url` 能配内网穿透（natapp/ngrok）就走沙箱真实支付；配不通时用单测/HTTP 客户端按支付宝报文格式**本地构造签名请求**验证验签与幂等逻辑——考点等价，不赌演示环境。
4. 保留 mock 通道，`@Profile` / 配置开关区分 dev 与 sandbox 环境。

**验收**：验签失败的伪造回调被拒绝并有日志；重复回调不重复发货；有条件时沙箱完成一笔真实支付闭环。
**工时**：1.5 天。

### 7.2 指标监控（Actuator + Prometheus + Grafana）

1. 各模块加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`；暴露 `health,info,prometheus`（仅内网端口，网关不路由 `/actuator`）。
2. compose 里加 prometheus + grafana；导入 Spring Boot 官方仪表盘（JVM、HTTP P99、MQ 消费速率），叠加 Sentinel 指标做一页演示大盘。

**验收**：Grafana 能看到各服务 QPS/RT/JVM，压测（4.4 的脚本）时曲线明显。
**工时**：1 天。

## 八、落地路线图

| 优先级 | 任务 | 工时 | 验收要点 |
|---|---|---|---|
| P0 | 3.1 JWT 密钥治理 | 0.5 天 | 明文出库、无 secret 拒绝启动 |
| P0 | 3.2 超时关单延迟消息 | 1 天 | 30 分钟准时关单、并发不误关 |
| P0 | 3.4 下单接口幂等 | 0.5 天 | 重复请求只产生一笔订单 |
| P0 | 3.3 Outbox 可靠发布 | 1.5 天 | MQ 宕机恢复后消息补发 |
| P1 | 4.1 CI | 0.5 天 | PR 自动测试与构建 |
| P1 | 5.1 AI 流式（后端 SSE） | 1 天 | curl 首字秒出、多轮记忆不丢 |
| P1 | 5.3 缓存一致性 | 1 天 | 延迟双删落地 |
| P1 | 5.2 RAG 语义搜书 | 2 天 | 语义召回图书 |
| P2 | 4.4 压测数据 | 0.5 天 | 缓存/流控对比数字可引用 |
| P2 | 6.1 Zipkin 链路追踪 | 0.5 天 | 跨服务调用树 |
| P2 | 4.2 容器化全栈 | 1.5 天 | 一条命令拉起全栈 |
| 选做 | 7.1 支付宝沙箱（简化版） | 1.5 天 | 验签/幂等考点闭环 |
| 选做 | 4.3 Testcontainers 单条 IT | 1 天 | verify 全绿 |
| 选做 | 7.2 Grafana 大盘 | 1 天 | 压测曲线可见 |

必做合计约 **10.5 个工作日**，选做池 3.5 天。

建议顺序：**3.1 → 3.2 → 4.1 → 3.4 → 3.3 → 5.1 → 5.3 → 5.2 → 6.1 → 4.4 → 4.2**。理由：P0 四项先还清安全与正确性欠债；4.1 半天见效立刻让后续所有改动有 CI 保护；AI 流式与 RAG 是项目最大差异化卖点，优先级高于可观测性；压测放在缓存优化之后做，数字最好看。

## 九、风险与注意事项

- **改名不动骨架**：品牌改名不涉及包名/服务名/端口，避免无意义的大重构。
- **延迟消息 TTL 队列**仅适用统一超时时长；未来若支持用户自选超时，需换 RabbitMQ 延迟插件或回归定时任务兜底。
- **密钥只进环境变量**：`JWT_SECRET`、`DASHSCOPE_API_KEY`、`ALIPAY_*` 一律不入库；`.env` 加入 `.gitignore`。
- **Nacos 占位符** `${JWT_SECRET:}` 依赖启动环境注入，`scripts/dev-macos.sh` 与 README 必须同步，否则新人起不来服务。
- **幂等唯一索引与历史数据**：`client_request_id` 允许 NULL（MySQL 唯一索引不去重 NULL），存量订单无需刷数据。
- **SSE 经网关透传**：网关 response-timeout 必须大于流式时长；Servlet 容器注意异步请求超时配置。
- **容器化后配置来源变化**：`localhost` 中间件地址需按 4.2 方案覆盖，落地时先在一个服务验证再铺开。
- **Testcontainers** 需要 Docker；CI runner 自带，本地开发需确认 Docker Desktop 运行（无 Docker 时 `-DskipITs`）。
- 每项落地后按仓库约定同步 `README.md`、`说明文档/`、`sql/`、`nacos-config/` 与 `AGENTS.md`。
