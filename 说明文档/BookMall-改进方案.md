# 墨枢 InkNexus · 改进方案（简历亮点冲刺版）

> 定位：秋招简历「亮点」冲刺（约 3 天），目标是让简历项目页多出几条有代码、有数字、经得起追问的实心条目。
> 原则：小改动、低风险、每项独立提交、独立可回滚；不删改既有接口契约与表结构语义，只新增可空列、新增队列/端点/配置。
> 本版取代此前「面试冲刺版」（已删除）；长期演进规划（Outbox 可靠投递、RAG、秒杀、全栈容器化等）见 Git 历史 commit `68325bd`，本轮不做。

## 一、已有亮点盘点（零开发，直接写上简历）

| 亮点 | 代码位置 | 简历参考话术 |
|---|---|---|
| AI 客服 Agent（Function Calling） | `bookmall-ai`：`BookAssistantAiService` + `QueryBookTool`/`QueryOrderTool` + `RedisChatMemoryStore` | 基于 LangChain4j + 通义千问实现电商客服 Agent，通过 Function Calling 查询图书/订单，Redis 维持多轮会话上下文 |
| 库存三态模型防超卖 | `StockMapper.xml`（条件原子 UPDATE）、`StockServiceImpl`（预占/确认/释放） | 采用「可售/锁定/确认」三态库存模型，条件原子更新防止超卖，释放幂等兜底 |
| 多层幂等 | `PaymentServiceImpl`（支付单状态）、`PaySuccessConsumer`（订单状态）、`closeExpiredOrder`（条件更新） | 支付、MQ 消费、超时关单三层幂等设计，保证重复请求/消息不产生副作用 |
| 网关统一鉴权 | `bookmall-gateway` `AuthGlobalFilter` | 网关统一 JWT 鉴权并注入用户身份，下游服务不信任客户端请求头 |
| 全局异常 + 参数校验 | `bookmall-common` `GlobalExceptionHandler`、DTO `@Valid` | 全局异常处理与统一返回体，DTO 校验前置 |
| 下单价格快照 | `OrderServiceImpl#createOrder` | 下单即快照价格，规避后续改价影响历史订单 |
| 单元测试 | 各模块 `src/test`（JUnit 5 + Mockito） | 核心业务全覆盖单元测试，不依赖基础设施可独立运行 |

## 二、冲刺总览

| 序 | 任务 | 工时 | 新增简历句（产出） | 面试考点 |
|---|---|---|---|---|
| 1 | 下单接口幂等 | 0.5 天 | 基于唯一索引实现下单幂等，防止重复提交导致重复预占 | 防重、幂等语义 |
| 2 | 超时关单：轮询 → 延迟消息 | 1 天 | 基于 RabbitMQ TTL+死信队列实现订单超时自动关闭，辅以定时扫描兜底 | 延迟消息、最终一致 |
| 3 | AI 助手 SSE 流式输出 | 1 天 | SSE 流式输出优化大模型交互体验（打字机效果） | 大模型工程化、流式协议 |
| 4 | 接口压测出数字 | 0.5 天 | （数字回填到上面任意一条句尾：压测 QPS 从 X 到 Y） | 性能量化、缓存收益 |

建议顺序：**1 → 2 → 3 → 4**（幂等先行，压测放最后测的是改造后的系统）。

## 三、分项实施

### 3.1 下单接口幂等（0.5 天）

**现状**：`OrderCreateRequest` 无客户端请求号，`createOrder` 直接插入，连点两次生成两笔订单、重复预占库存。

**步骤**：

1. 新增 `sql/updates/006_order_request_id.sql`：

```sql
ALTER TABLE `t_order`
  ADD COLUMN `client_request_id` VARCHAR(64) NULL COMMENT '客户端请求号，下单幂等防重',
  ADD UNIQUE KEY `uk_user_request` (`user_id`, `client_request_id`);
```

可空列 + 联合唯一：不同用户请求号互不影响；MySQL 唯一索引不去重 NULL，存量订单与老请求完全不受影响。

2. `OrderCreateRequest` / `OrderFromCartRequest` 增加可选字段 `clientRequestId`，`insertOrderHead` 透传写入。
3. 插入订单捕获 `DuplicateKeyException` → 按 `(user_id, clientRequestId)` 查回已存在订单直接返回（幂等语义：重复提交返回同一笔订单）。
4. 前端配合项：下单时生成 UUID 传入（一行代码）；后端对不传该字段的请求保持完全兼容。

**验收**：同一 `clientRequestId` 重复调用只产生一笔订单、库存只预占一次；不同用户请求号相同互不影响。

### 3.2 超时关单：轮询 → TTL + 死信延迟消息（1 天）

**现状**：`OrderTimeoutTask` 每 30 秒扫 `status=0 且 expire_time<=now` 的订单，延迟最高 30 秒且持续空扫。

**设计要点**：

- 本项目订单过期时间统一为 `orderExpireMinutes`（全局配置），因此用**队列级 TTL** 单队列即可，天然规避 RabbitMQ「单队列内按消息 TTL 有队头阻塞」的坑——这一点本身就是面试加分话术。
- 消费端幂等零成本：`closeExpiredOrder` 是条件更新（`status=0 且已过期` 才置为关闭），重复消费/已支付/已取消订单自动 no-op。

**步骤**：

1. `BookMallRabbitMq` 新增拓扑常量；在声明类中创建：
   - 延迟队列 `bookmall.order.close.delay.queue`：`x-message-ttl` = 过期分钟数×60000，`x-dead-letter-exchange` = `bookmall.order.close.exchange`（direct，无路由键routing key直接投递）。
   - 死信队列 `bookmall.order.close.queue` 绑定到该交换机。
2. 订单创建成功后（`insertOrderHead` 之后）向延迟队列发布 `{orderId}` 关单消息。
3. 新增 `OrderCloseDelayConsumer` 消费死信队列，调用现有 `orderService.closeExpiredOrder(orderId)`，复用其幂等与库存释放逻辑。
4. `OrderTimeoutTask` 保留作兜底，cron 放宽到 1~5 分钟。

**验收**：待支付订单到期后约秒级被延迟消息关闭并释放库存；已支付订单收到关单消息无副作用；轮询兜底仍可清理漏网订单。

### 3.3 AI 助手 SSE 流式输出（1 天）

**现状**：`AiModelConfig` 用 `OpenAiChatModel`（DashScope 兼容模式）同步返回整段；链路 `AiAssistantController#chat` → `ChatServiceImpl` → `@AiService` 代理。

**步骤**：

1. `AiModelConfig` 新增 `OpenAiStreamingChatModel` Bean（同 `baseUrl`/`apiKey`，兼容模式支持流式）。
2. `BookAssistantAiService` 新增返回 `TokenStream` 的方法（LangChain4j `@AiService` 原生支持，系统提示词、记忆、`@Tool` 工具调用在流式下照常生效）。
3. `ChatService` 新增 `stream` 方法；Controller 新增 `POST /ai/chat/stream`，返回 `SseEmitter`：`onPartialResponse` 回调中逐段 `send`，`onCompleteResponse` 中 `complete`，异常时记录日志并 `completeWithError`。原 `/ai/chat` 保留作为降级。
4. 网关（Spring Cloud Gateway 基于 Netty）原生支持流式响应，注意确认响应超时设置大于模型最大耗时。
5. 前端 `AiChatView.vue`：SSE 的 `EventSource` 不支持 POST，改用 `fetch` + `ReadableStream` 手动解析 SSE 帧，逐字追加渲染打字机效果。

**验收**：浏览器可见逐字输出；多轮会话记忆仍生效；询问图书/订单时 Function Calling 正常；接口经网关 `/api/ai/chat/stream` 可用。

### 3.4 接口压测与简历数字（0.5 天）

1. JMeter 两个场景：
   - 图书分页列表：对比「清 Redis 后冷启动」与「命中缓存」的 QPS / P95（对应 `@Cacheable` 30 分钟 TTL 的收益）。
   - 下单链路：固定测试账号与充足库存，压直接下单接口，记录吞吐与错误率。
2. 数字回填：README「项目亮点」与简历对应条目句尾（例：「引入 Redis 缓存后列表接口 QPS 从 X 提升到 Y，P95 从 A ms 降至 B ms」）。
3. 压测报告整理到 `说明文档/BookMall-压测报告.md`（场景、参数、结果表）。

## 四、顺手可选项（每项 ≤ 半小时，不阻塞主线）

- **JWT 密钥治理**：`nacos-config/auth.yaml`、`gateway.yaml` 的 secret 改 `${JWT_SECRET:}` 环境变量，`JwtUtil`/`AuthGlobalFilter` 启动时校验长度 ≥ 32 并轮换新密钥（旧密钥已入 Git 历史，视为已泄露）。
- **Feign 超时**：各服务 `connectTimeout`/`readTimeout` 配置，明确预占接口不重试（幂等先行）。
- **GitHub Actions CI**：push/PR 自动 `mvn test`，README 挂徽章。

## 五、本轮明确不做

MQ 可靠投递（publisher confirm / Outbox）、秒杀（Redis+Lua）、多级缓存、链路追踪（Zipkin/SkyWalking）、支付沙箱对接、全栈容器化——留给后续轮次，完整长期规划见 Git 历史 `68325bd`。

## 六、统一验收清单

- [ ] `mvn -f BookMall/pom.xml -q test` 全量通过
- [ ] 登录 → 浏览 → 下单 → 支付 → 超时关单主链路手工回归无回归
- [ ] README / `说明文档/` / `sql/sql.txt` 与代码同步更新（含压测数字）
- [ ] 每项独立提交（Conventional Commits，如 `feat(order)`、`feat(ai)`），可单独回滚
