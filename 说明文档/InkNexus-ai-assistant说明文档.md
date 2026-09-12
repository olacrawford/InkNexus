# 墨枢 InkNexus · ai-assistant 模块说明文档

## 1. 当前职责

`inknexus-ai` 是 AI 问答助手模块（服务名 `ai-assistant`，端口 `8071`），使用 LangChain4j 接入阿里云通义千问（DashScope），实现「图书咨询 + 我的订单查询」对话能力。

当前已实现：

- `POST /ai/chat`：接收用户消息，AI 结合工具回答
- `POST /ai/chat/stream`：SSE 流式对话，逐 token 推送（`/ai/chat` 保留为降级接口）
- `GET /ai/hello`：健康检查
- 工具：`searchBooks` / `listCategories` / `queryMyOrders` / `queryOrderDetail`
- 只读：仅通过 OpenFeign 调 book / order 的查询接口，不参与下单、支付、退款、取消
- 会话记忆存 Redis，按用户 + 会话隔离，带 TTL
- `X-User-Id` 由网关透传，Feign 回源时原样转发，不接受模型传入的 userId

## 2. 当前项目结构

- 启动类：`com.inknexus.ai.AiAssistantApplication`
- `config`：`AiModelConfig`、`ChatMemoryConfig`、`FeignAuthConfig`、`WebMvcConfig`
- `ai`：`BookAssistantAiService`（`@AiService` + SystemMessage）
- `tool`：`QueryBookTool`、`QueryOrderTool`
- `feign`：`BookFeignClient`、`OrderFeignClient`、`feign/dto/*`
- `service`：`ChatService`、`service.impl.ChatServiceImpl`
- `controller`：`AiAssistantController`
- `context`：`UserContextHolder`、`UserContextInterceptor`
- `support`：`ResultUtils`、`RedisChatMemoryStore`
- `dto`：`ChatRequest`、`ChatResponse`
- 资源：`src/main/resources/application.yml`
- 测试：`ResultUtilsTest`、`ChatServiceImplTest`

## 3. 配置说明

- 端口：`8071`
- 服务名：`ai-assistant`
- Nacos 注册中心：`localhost:8848`
- Nacos Config：`ai-assistant.yaml`（含 Redis、Feign 超时、DashScope 参数、会话 TTL）
- Redis：`localhost:6379`
- DashScope：`baseUrl` = `https://dashscope.aliyuncs.com/compatible-mode/v1`，模型 `qwen-plus`
- `dashscope.api-key` 使用 `${DASHSCOPE_API_KEY:}`，真实 Key 走环境变量，不进 Git
- 本地 `application.yml` 只保留端口、服务名、Nacos 地址

配置文件：

- `inknexus-ai/src/main/resources/application.yml`
- `nacos-config/ai-assistant.yaml`

## 4. 当前接口

接口前缀：`/ai`。通过网关访问时前缀为 `/api/ai`。

### 4.1 GET /ai/hello

健康检查，返回 `Result<String>`：`"ai-assistant is running"`。

### 4.2 POST /ai/chat

AI 对话。请求头必须携带 `X-User-Id`（网关注入）。

请求体：

```json
{
  "message": "帮我推荐几本关于AI的书",
  "conversationId": "可选"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "reply": "推荐这几本...",
    "conversationId": "生成的会话ID"
  }
}
```

`conversationId` 缺省时服务端自动生成，作为 Redis 会话记忆的隔离键。

### 4.3 POST /ai/chat/stream

AI 流式对话（SSE）。请求头、请求体与 `POST /ai/chat` 完全一致，会话记忆与 Function Calling 工具调用共用同一条链路；`POST /ai/chat` 保留作为降级接口。

响应为 `text/event-stream`，事件协议：

| 事件 | data 内容 | 说明 |
|---|---|---|
| `meta` | 会话 ID | 首个事件，前端保存后用于多轮记忆 |
| `delta` | 增量文本片段 | 逐 token 推送，前端追加渲染打字机效果 |
| `done` | `[DONE]` | 回复结束，连接关闭 |

实现说明：

- `AiModelConfig` 同时声明 `ChatModel` 与 `StreamingChatModel`（OpenAI 兼容模式 stream 接口），LangChain4j 据此为 `@AiService` 接口的 `TokenStream chatStream(...)` 方法绑定流式模型
- 控制器用 Spring MVC `SseEmitter` 桥接 `TokenStream` 回调，超时 120 秒
- `EventSource` 不支持 POST，前端用 `fetch` + `ReadableStream` 解析 SSE 帧（`front/src/api/inknexus.js` 的 `aiChatStream`），流式失败自动降级 `POST /ai/chat`
- 网关为 Spring Cloud Gateway（Netty），原生透传流式响应，无需额外配置

## 5. 依赖的现有服务接口

- `GET /books/page`：图书分页（`BookFeignClient.pageBooks`）
- `GET /books/categories`：分类（`BookFeignClient.listCategories`）
- `GET /orders`：当前用户订单列表（`OrderFeignClient.listOrders`，需 `X-User-Id`）
- `GET /orders/{id}`：订单详情（`OrderFeignClient.getOrderDetail`，需 `X-User-Id`）

全部为只读接口；`X-User-Id` 通过 Feign `RequestInterceptor` 从 `UserContextHolder` 注入。

## 6. 前端页面

已接入前端 `front/`，入口为侧边栏「AI 助手」（`/ai` 路由，需登录）：

- 页面：`front/src/views/AiChatView.vue`，包含会话气泡、快捷提问、新对话清空
- API：`front/src/api/inknexus.js` 中的 `aiApi.chat()` 调用 `POST /api/ai/chat`，`aiApi.hello()` 调用 `GET /api/ai/hello`
- 路由：`front/src/router/index.js` 新增 `/ai`（`meta.auth = true`）
- 会话隔离：前端用 `localStorage` 保存 `conversationId` 并随请求下发，服务端按「用户 + 会话」隔离记忆

前端访问入口：`http://localhost:5173/ai`（启动前端后登录即可看到）。

## 7. 验证与测试

### 7.1 单元测试

- `ResultUtilsTest`：`Result` 解包成功返回 payload、非 200 抛 `BusinessException`、空结果抛异常
- `ChatServiceImplTest`：缺省会话 ID 时自动生成、指定会话 ID 时构造 `userId:conversationId` 记忆键

运行：

```bash
mvn -f InkNexus/pom.xml -pl inknexus-ai -am test
```

### 7.2 运行验证

启动完整后端并给网关透传 token 后：

```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"message":"推荐几本关于AI的书"}'
```

返回 `code=200`，`data.reply` 为模型回复，`data.conversationId` 为本次会话 ID。

### 7.3 健康检查

```text
GET http://localhost:8080/api/ai/hello
```

返回 `Result<String>`：`ai-assistant is running`。

## 8. 启动

```bash
export DASHSCOPE_API_KEY='sk-你的通义千问Key'
mvn -f InkNexus/pom.xml -DskipTests install
mvn -f InkNexus/pom.xml -pl inknexus-ai spring-boot:run
```

网关路由已在 `inknexus-gateway/src/main/resources/application.yml` 配置 `/api/ai/**` → `lb://ai-assistant`。

> 说明：`/ai/chat` 会真实调用通义千问（DashScope），必须有有效的 `DASHSCOPE_API_KEY` 才能返回模型回复；Key 留空时建议仍可启动并返回健康检查，但对话会因模型鉴权失败而报错。当前未内置 Mock 模型，若希望调试不消耗额度，可在 `config/AiModelConfig` 临时换成返回固定文案的替身模型（属于可选增强，尚未实现）。

---
*历史设计（施工文档）已归档至 `archive/ai-assistant-技术设计文档.md`，本文档为当前实现口径。*
