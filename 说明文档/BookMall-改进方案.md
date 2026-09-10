# 墨枢 InkNexus · 改进方案

> 定位：从「功能跑通的演示项目」升级为「具备生产思维的架构展示项目」。
> 口径：本文档是**演进规划**，与「当前已实现」口径的模块文档区分；每项落地后在对应模块文档同步补充。
> 日期：2026-09-10

## 一、现状评估

**已具备**：六服务 + 网关 + AI 助手的完整拆分；Nacos 注册与配置；网关统一 JWT 鉴权与 `X-User-Id` 透传；Redis 缓存与 Sentinel 流控；RabbitMQ 支付成功/库存确认释放事件（消费端按订单状态幂等）；库存预占→支付确认→超时释放闭环；9 个模块 Mockito 单元测试；Knife4j 文档；完善的中文文档体系。

**主要短板**：

| # | 短板 | 现状证据 |
|---|---|---|
| 1 | JWT 密钥硬编码且已提交 Git，auth/gateway 两处重复 | `nacos-config/auth.yaml`、`nacos-config/gateway.yaml` 均为 `bookmall-jwt-secret-key-2026-safe` |
| 2 | 订单超时靠 30 秒轮询，延迟不精确、空扫 DB | `bookmall-order/.../task/OrderTimeoutTask.java`（cron `0/30 * * * * ?`） |
| 3 | 支付为纯 mock，链路缺真实支付语义 | `PaymentServiceImpl#createPayment` 落单即 `status=1`、`payType="mock"` |
| 4 | MQ 生产端「事务内直接发」，非真正可靠 | `PaymentServiceImpl#pay` 事务内 `publishPaySuccess`；未开启 publisher confirm |
| 5 | 无 CI / 无后端镜像 / 无集成测试 | 仓库无 `.github/workflows`；仅 `front/` 有 Dockerfile |
| 6 | 无链路追踪与指标监控 | 各模块无 micrometer-tracing / actuator 暴露配置 |
| 7 | AI 助手同步阻塞返回，检索仅靠 Tool 查库 | `ChatServiceImpl#chat` 同步返回；无流式、无向量检索 |
| 8 | 前端无状态管理与 UI 库，token 存 localStorage | `front/package.json` 仅 vue/vue-router/axios；`utils/session.js` 直用 localStorage |

## 二、命名与品牌

> 状态：**已落地（2026-09-10）**。选定 **墨枢 · InkNexus**（备选：文渊 LibraVerse、书灵 BookSage、翰林云商 HanCloud Books）。
> 「枢」呼应网关中枢 + AI 大脑的架构定位，中英文都适合写进简历。

改名落地清单（仅品牌层，**不改** Git 仓库名、Maven `groupId/artifactId`、包名 `com.bookmall`、端口与 Nacos 服务名、数据库名与本地存储 key，避免大规模重构）：

- [x] `README.md` 标题与首段：`# 墨枢 InkNexus（BookMall）`
- [x] `front/index.html` 的 `<title>` 与 `<meta description>`
- [x] 前端品牌文案：侧边栏（`App.vue`）、登录页（`LoginView.vue`）、总览页（`HomeView.vue`）
- [x] `说明文档/` 各文档 H1 统一为「墨枢 InkNexus · …」
- [ ] `docker-compose.infra.yml` 项目名保持 `bookmall-infra`：compose 项目名一旦变更会与运行中的旧容器并存并抢占端口，不值得为品牌冒环境风险

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

### 3.3 支付宝沙箱支付

**现状**：`PaymentServiceImpl#pay` 落单即视为已支付，`payType="mock"`，无真实支付语义。

**实施步骤**：

1. `bookmall-payment/pom.xml` 引入 `com.alipay.sdk:alipay-sdk-java`（4.38+）。
2. `nacos-config/payment.yaml` 增加密钥占位（**密钥只放环境变量**，遵守仓库安全约定）：

```yaml
alipay:
  gateway-url: https://openapi-sandbox.dl.alipaydev.com/gateway.do
  app-id: ${ALIPAY_APP_ID:}
  app-private-key: ${ALIPAY_PRIVATE_KEY:}
  alipay-public-key: ${ALIPAY_PUBLIC_KEY:}
  notify-url: ${ALIPAY_NOTIFY_URL:}   # 内网穿透地址，如 natapp/ngrok
```

3. 支付流程拆为两段：
   - `POST /api/payment/alipay/{orderId}`：校验订单（复用现有 `getPayableOrder`）→ 落 `status=0` 待支付支付单 → 调 `alipay.trade.page.pay` 返回支付表单/跳转 URL；
   - `POST /api/payment/alipay/notify`：**验签** → 校验金额与订单号 → 幂等更新支付单为已支付（按 `payment.status` 判断，复用 `publishPaySuccess` 发布事件）→ 返回 `"success"`。支付宝会重试通知，幂等逻辑必须有。
4. 保留 mock 通道，用 `@Profile` / 配置开关区分 dev 与 sandbox 环境。
5. 文档补充沙箱账号申请、密钥生成（密钥工具）、内网穿透配置步骤。

**验收**：沙箱账号完成一笔真实支付，订单经 MQ 异步变为已支付、库存确认；重复回调不重复发货；验签失败的伪造回调被拒绝并有日志。
**工时**：2 天。

### 3.4 本地消息表（Outbox）保证事件可靠发布

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

### 4.3 Testcontainers 集成测试 + JaCoCo

1. 选真实依赖最重的链路先做两个 IT（放 `bookmall-order`、`bookmall-stock`）：

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

2. 跨服务 Feign 调用用 WireMock 桩掉，保持 IT 单服务内闭环。
3. 父 POM 加 `jacoco-maven-plugin`，`check` 阶段设行覆盖率阈值（建议起步 50%，逐模块调），CI 中 `mvn verify` 生效。

**验收**：`mvn -f BookMall/pom.xml verify` 本地与 CI 均绿；覆盖率报告生成。
**工时**：2 天。

## 五、P2 可观测性

### 5.1 链路追踪（Micrometer Tracing + Zipkin）

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

### 5.2 指标监控（Actuator + Prometheus + Grafana）

1. 各模块加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`。
2. 配置暴露端点（仅内网端口，网关不路由 `/actuator`）：

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus
```

3. compose 里加 prometheus + grafana；导入 Spring Boot 官方仪表盘（JVM、HTTP P99、MQ 消费速率），叠加 Sentinel 指标做一页演示大盘。

**验收**：Grafana 能看到各服务 QPS/RT/JVM，压测（JMeter 简单脚本即可）时曲线明显。
**工时**：1 天。

## 六、P3 功能增强

### 6.1 AI 助手 SSE 流式输出

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

3. 前端 `AiChatView` 因需带 `Authorization` 头，改用 `@microsoft/fetch-event-source`（原生 `EventSource` 不支持自定义头），逐字渲染气泡。
4. Redis 会话记忆写入时机改为完整回复拼装完成后，保持现有 memory 结构不变。

**验收**：提问后首字 1~2 秒内出现，逐字流式渲染，多轮记忆不丢。
**工时**：1.5 天。

### 6.2 语义搜书（RAG）

**实施步骤**：

1. 嵌入模型：`langchain4j-embeddings-bge-small-zh-v15`（本地运行，零 API 成本，中文效果好）；书目为千级以下，向量存储用 `InMemoryEmbeddingStore` + 启动时从 book 表全量构建并持久化快照，规模化再换 Milvus。
2. 提供管理端点 `POST /api/ai/admin/reindex` 重建向量索引（服务间用 Feign 从 book 服务拉全量书目）。
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

### 6.3 前端现代化

1. **Pinia 接管会话**：新增 `stores/auth.js`，内部迁移 `utils/session.js` 的 localStorage 逻辑但**保持对外导出函数签名不变**（`isLoggedIn`/`getToken` 等），7 个视图零改动切换。
2. **UI 库**：引入 Element Plus（按需导入），优先替换 BooksView/CartView/OrdersView 的表格、弹窗、消息提示；`styles/main.css` 手写样式逐步收敛为主题变量。
3. **测试**：Vitest + @vue/test-utils，先覆盖 `stores/auth.js` 与路由守卫，CI 的 frontend job 追加 `npm run test`。
4. **token 安全（可选）**：auth 服务增加 refresh-token 接口，access token 有效期降到 30 分钟，前端 401 时静默刷新；localStorage 只存 refresh token。

**验收**：登录态在刷新页面后保持；核心页面无样式回退；`npm run test` 进 CI。
**工时**：3 天（token 刷新另加 1 天）。

### 6.4 缓存一致性强化

1. 核对 `BookServiceImpl`/`CategoryServiceImpl` 的写路径：更新/删除必须按 key 失效缓存，空值缓存 TTL 要短于正常缓存（防穿透）。
2. 引入**延迟双删**：删缓存 → 更新 DB → 延迟 500ms 再删一次（用 `CompletableFuture.delayedExecutor` 或 MQ 延迟消息），覆盖「删后读旧值回填」窗口。
3. 进阶亮点：接 Canal 订阅 binlog 失效缓存（可只写设计文档作为演进项）。

**验收**：并发「读-更新」测试下，缓存最终一致；文档补一致性方案说明。
**工时**：1 天。

## 七、落地路线图

| 阶段 | 任务 | 工时 | 验收要点 |
|---|---|---|---|
| P0 | 3.1 JWT 密钥治理 | 0.5 天 | 明文出库、无 secret 拒绝启动 |
| P0 | 3.2 超时关单延迟消息 | 1 天 | 30 分钟准时关单、并发不误关 |
| P0 | 3.3 支付宝沙箱 | 2 天 | 沙箱真实支付闭环、回调幂等 |
| P0 | 3.4 Outbox 可靠发布 | 1.5 天 | MQ 宕机恢复后消息补发 |
| P1 | 4.1 CI | 0.5 天 | PR 自动测试与构建 |
| P1 | 4.2 容器化全栈 | 1.5 天 | 一条命令拉起全栈 |
| P1 | 4.3 IT + JaCoCo | 2 天 | verify 全绿、覆盖率门槛 |
| P2 | 5.1 链路追踪 | 0.5 天 | Zipkin 跨服务调用树 |
| P2 | 5.2 指标监控 | 1 天 | Grafana 大盘 |
| P3 | 6.1 AI 流式 | 1.5 天 | 首字秒出、逐字渲染 |
| P3 | 6.2 RAG 语义搜书 | 2 天 | 语义召回图书 |
| P3 | 6.3 前端现代化 | 3 天 | Pinia + UI 库 + 测试进 CI |
| P3 | 6.4 缓存一致性 | 1 天 | 延迟双删落地 |

建议顺序：**3.1 → 3.2 → 4.1 → 3.4 → 5.1**（两周内可完成，性价比最高），3.3 与 P3 按兴趣穿插。

## 八、风险与注意事项

- **改名不动骨架**：品牌改名不涉及包名/服务名/端口，避免无意义的大重构。
- **延迟消息 TTL 队列**仅适用统一超时时长；未来若支持用户自选超时，需换 RabbitMQ 延迟插件或回归定时任务兜底。
- **沙箱密钥**（`ALIPAY_*`）与 `JWT_SECRET`、`DASHSCOPE_API_KEY` 一致：只进环境变量，绝不入库；`.env` 加入 `.gitignore`。
- **Nacos 占位符** `${JWT_SECRET:}` 依赖启动环境注入，`scripts/dev-macos.sh` 与 README 必须同步，否则新人起不来服务。
- **容器化后配置来源变化**：`localhost` 中间件地址需按 4.2 方案覆盖，落地时先在一个服务验证再铺开。
- **Testcontainers** 需要 Docker；CI runner 自带，本地开发需确认 Docker Desktop 运行。
- 每项落地后按仓库约定同步 `README.md`、`说明文档/`、`sql/`、`nacos-config/` 与 `AGENTS.md`。
