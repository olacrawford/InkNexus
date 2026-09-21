# InkNexus · inknexus-ai 迁移 Spring AI 说明

> 完成日期：2026-09-19。`inknexus-ai` 已从 LangChain4j 1.19.0 完全迁移到 Spring AI 1.1.5（`spring-ai-starter-model-deepseek`），模型供应商从 DashScope 通义千问（qwen-plus）切换为 DeepSeek（`deepseek-chat`）。前置条件是本次同日完成的全项目 Spring Boot 3.5.16 升级（见 `InkNexus-SpringBoot升级说明.md`）。对外 REST/SSE 契约不变，前端零改动。

## 1. 新旧技术栈对照

| 能力 | 迁移前（LangChain4j） | 迁移后（Spring AI） |
|---|---|---|
| 依赖 | `langchain4j` + `langchain4j-spring-boot-starter` + `langchain4j-open-ai`（BOM 1.19.0） | `spring-ai-starter-model-deepseek`（BOM 1.1.5，父 POM `spring-ai-bom`） |
| 模型接入 | 手写 `OpenAiChatModel`/`OpenAiStreamingChatModel` Builder（`AiModelConfig`），DashScope 兼容模式 | starter 按 `spring.ai.deepseek.*` 自动装配 DeepSeekChatModel，无需手写 |
| AI 服务 | `@AiService` 接口 `BookAssistantAiService`（同步 `String` + `TokenStream` 两个方法） | 单个 `ChatClient` Bean：`defaultSystem` + `defaultTools` + `MessageChatMemoryAdvisor`，`.call()` 同步 / `.stream()` 流式 |
| 会话记忆 | 自研 `RedisChatMemoryStore`（实现 `ChatMemoryStore`）+ `ChatMemoryProvider` + `MessageWindowChatMemory` | 自研 `RedisChatMemoryRepository`（实现 `ChatMemoryRepository` SPI）+ `MessageWindowChatMemory`（窗口 20 条不变） |
| 工具声明 | `@Tool("描述")` + `@P("参数描述")`，返回手工拼接的中文文本行 | `@Tool(description=...)` + `@ToolParam(description=..., required=...)`，返回结构化 record 由框架序列化为 JSON；工具异常经 `ToolExecutionExceptionProcessor` 转成模型可读说明，对话不中断 |
| 用户身份传递 | 依赖请求线程 ThreadLocal（流式回调线程读不到，属已知隐患） | `ChatClient` 请求级 `toolContext(Map.of("userId", ...))` 显式传入工具，再写 ThreadLocal 供 Feign 透传（不挑执行线程，隐患消除） |
| SSE 流式 | `TokenStream` 回调手动桥接 `SseEmitter`（120s 超时代码控制），会话 ID 与流捆绑在自定义 `StreamChatSession` 里 | 控制器直接返回 `Flux<ServerSentEvent<String>>`（Spring MVC 响应式桥），服务层返回纯内容流 `Flux<String>`，会话 ID 由控制器归一并随 `meta` 事件下发，超时走 `spring.mvc.async.request-timeout: 120s` |
| 顾问链 | 无（记忆由 @AiService 的 memoryId 隐式生效） | 显式顾问链：`MessageChatMemoryAdvisor`（会话记忆）+ `SimpleLoggerAdvisor`（DEBUG 级提示词日志） |
| 配置键 | 自定义 `dashscope.*` 前缀，`DASHSCOPE_API_KEY` | 官方 `spring.ai.deepseek.*` 前缀，`DEEPSEEK_API_KEY` |

## 2. 代码改动清单

- 删除：`ai/ai/BookAssistantAiService`（`@AiService` 接口）、`config/AiModelConfig`、`support/RedisChatMemoryStore`、`dto/StreamChatSession`（流式改为服务返回 `Flux<String>` 内容流、控制器组装 SSE）
- 新增：`config/ChatClientConfig`（ChatClient 装配：系统提示词 + 工具 + 顾问链 + 工具异常转换器）、`support/RedisChatMemoryRepository`
- 重写：`config/ChatMemoryConfig`、`service/ChatService`、`service/impl/ChatServiceImpl`、`controller/AiAssistantController`、`tool/QueryBookTool`、`tool/QueryOrderTool`
- 不变：Feign 客户端与快照 DTO、`UserContextHolder`/`UserContextInterceptor`/`FeignAuthConfig`/`WebMvcConfig`、`ResultUtils`、`ChatRequest`/`ChatResponse`
- 测试：`ChatServiceImplTest` 重写为 mock `ChatClient` 链式调用（含 advisors 参数捕获，校验 `userId:conversationId` 记忆键确实传入）
- POM：父 POM 以 `spring-ai-bom` 1.1.5 取代 `langchain4j-bom`；模块依赖换成 `spring-ai-starter-model-deepseek`
- 配置：`nacos-config/ai-assistant.yaml` 改为 `spring.ai.deepseek.*`（api-key/base-url/model/temperature/max-tokens），删除代码从未读取的 `tool-result-limit` 键

## 3. 兼容性要点

- **Redis 记忆格式完全沿用**：键 `chat:memory:{userId}:{conversationId}`、JSON 数组 `{type,text}`、TTL 2 小时每次写入刷新，因此升级上线后用户的进行中会话记忆无缝延续。
- **SSE 协议不变**：`meta`（会话 ID）→ `delta`（增量文本）→ `done`（`[DONE]`），前端 `aiChatStream` 与降级逻辑无需任何修改。
- **安全链路不变**：模型参数里没有 userId；订单工具只能查 `toolContext` 里网关信任链传来的当前用户，order 服务继续按 X-User-Id 校验归属。

## 4. 验证记录

1. `mvn -f InkNexus/pom.xml test`：10 个模块全部通过（ai 模块 7 例，其余 74 例回归无影响）。
2. 运行冒烟（占位 API Key）：服务 2.6 秒启动，Spring AI 自动装配无报错；`nacos registry ... ai-assistant ... register finished`；`GET /ai/hello` 正常；`POST /ai/chat/stream` 首帧为 `event:meta` + `data:<会话ID>`，与迁移前帧格式一致；占位 Key 被 DeepSeek 拒绝后流式 WARN 日志正常、同步接口按统一 `Result` + traceId 包装错误。
3. 流式回调确认运行在 `boundedElastic` 线程——验证了 ToolContext 方案的必要性（请求线程 ThreadLocal 在该线程不可见）。

## 5. 待办与注意事项

- 真实对话（含工具调用链）需在设置了 `DEEPSEEK_API_KEY` 的环境启动后验证：`export DEEPSEEK_API_KEY='sk-...'` 再起服务，前端 `http://localhost:5173/ai` 走一轮「图书咨询 + 我的订单」。
- DeepSeek 的 function calling 仅 `deepseek-chat` 支持，勿将 `spring.ai.deepseek.chat.options.model` 改为不支持工具调用的模型。
- `说明文档/InkNexus-面试问答准备.md` 中关于 LangChain4j 实现口径的段落未同步（属面试话术材料，保持原样或按需自行改写）；`archive/` 内历史文档按约定不改。
