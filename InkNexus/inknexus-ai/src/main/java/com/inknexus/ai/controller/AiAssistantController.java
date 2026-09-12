package com.inknexus.ai.controller;

import com.inknexus.ai.dto.ChatRequest;
import com.inknexus.ai.dto.ChatResponse;
import com.inknexus.ai.dto.StreamChatSession;
import com.inknexus.ai.service.ChatService;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/** AI 助手对外 REST Controller：暴露“健康检查”“AI 对话”和“AI 流式对话”三个接口。 */
@Slf4j
@RestController // 返回值自动写成 JSON
@RequestMapping("/ai") // 统一接口前缀；经网关访问时是 /api/ai
@RequiredArgsConstructor // Lombok：自动注入 final 的 ChatService
public class AiAssistantController {

    /** SSE 连接最长保持时间，需大于模型最大回复耗时。 */
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final ChatService chatService;

    /** 健康检查，用于确认 ai 服务已启动。 */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("ai-assistant is running");
    }

    /**
     * AI 对话入口（同步返回完整回复，作为流式接口的降级备选）。
     *
     * @param userId 网关注入的 X-User-Id，代表当前登录用户
     * @param request 请求体：用户消息 + 可选会话 ID
     * @return Result 包装的 AI 回复与会话 ID
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestHeader("X-User-Id") Long userId,
                                     @Valid @RequestBody ChatRequest request) { // @Valid 触发 @NotBlank 校验
        return Result.success(chatService.chat(userId, request));
    }

    /**
     * AI 流式对话入口（SSE）。
     * <p>事件协议：{@code meta} 推送会话 ID；{@code delta} 逐段推送回复内容；{@code done} 表示结束。
     * 前端 EventSource 不支持 POST，需用 fetch + ReadableStream 解析。
     *
     * @param userId 网关注入的 X-User-Id，代表当前登录用户
     * @param request 请求体：用户消息 + 可选会话 ID
     * @return SseEmitter，由 TokenStream 回调逐段写入
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader("X-User-Id") Long userId,
                                 @Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        emitter.onTimeout(emitter::complete);

        StreamChatSession session = chatService.streamChat(userId, request);
        try {
            emitter.send(SseEmitter.event().name("meta").data(session.conversationId()));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        session.tokenStream()
                .onPartialResponse(token -> sendDelta(emitter, token))
                .onCompleteResponse(response -> completeStream(emitter))
                .onError(ex -> {
                    log.warn("AI 流式对话异常：userId={}", userId, ex);
                    emitter.completeWithError(ex);
                })
                .start();
        return emitter;
    }

    private void sendDelta(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(token));
        } catch (IOException ex) {
            // 客户端提前断开等情况：终止推送并停止后续回调
            emitter.completeWithError(ex);
        }
    }

    private void completeStream(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
