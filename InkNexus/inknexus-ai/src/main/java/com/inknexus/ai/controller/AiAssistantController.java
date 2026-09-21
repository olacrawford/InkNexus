package com.inknexus.ai.controller;

import com.inknexus.ai.dto.ChatRequest;
import com.inknexus.ai.dto.ChatResponse;
import com.inknexus.ai.service.ChatService;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** AI 助手对外 REST Controller：暴露“健康检查”“AI 对话”和“AI 流式对话”三个接口。 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("ai-assistant is running");
    }

    /** AI 对话入口（同步返回完整回复，作为流式接口的降级备选）。 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request);
        return Result.success(chatService.chat(userId, conversationId, request.getMessage()));
    }

    /**
     * AI 流式对话入口（SSE）。事件协议：{@code meta} 推送会话 ID；{@code delta} 逐段推送回复内容；
     * {@code done} 表示结束。Spring MVC 原生支持 Flux 返回值（响应式桥按流式推送，无需手动管理 emitter），
     * 连接超时由 spring.mvc.async.request-timeout 控制。前端 EventSource 不支持 POST，
     * 需用 fetch + ReadableStream 解析。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestHeader("X-User-Id") Long userId,
                                                    @Valid @RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request);
        Flux<ServerSentEvent<String>> meta = Flux.just(sse("meta", conversationId));
        Flux<ServerSentEvent<String>> deltas = chatService
                .streamChat(userId, conversationId, request.getMessage())
                .map(token -> sse("delta", token))
                .doOnError(ex -> log.warn("AI 流式对话异常：userId={}", userId, ex));
        Flux<ServerSentEvent<String>> done = Flux.just(sse("done", "[DONE]"));
        return Flux.concat(meta, deltas, done);
    }

    /**
     * 归一会话 ID：前端带回则沿用（同一会话连续对话），缺失或空白则生成新 UUID 开启新会话。
     * ID 属于 web 会话协议的一部分，由控制器负责生成并随 meta/响应体下发给前端。
     */
    private static String resolveConversationId(ChatRequest request) {
        return request.getConversationId() == null || request.getConversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getConversationId();
    }

    /** 按协议组装一个 SSE 事件（事件名 + 数据）。 */
    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }
}
