package com.inknexus.ai.service.impl;

import com.inknexus.ai.ai.BookAssistantAiService;
import com.inknexus.ai.dto.ChatRequest;
import com.inknexus.ai.dto.ChatResponse;
import com.inknexus.ai.dto.StreamChatSession;
import com.inknexus.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** AI 对话编排实现：负责生成会话 ID、构造记忆键，并调用 @AiService 拿到模型回复。 */
@Service
@RequiredArgsConstructor // Lombok：自动为 final 字段生成构造方法，便于注入
public class ChatServiceImpl implements ChatService {

    private final BookAssistantAiService aiService;

    @Override
    public ChatResponse chat(Long userId, ChatRequest request) {
        String memoryId = userId + ":" + resolveConversationId(request);
        // 调用 LangChain4j 生成的代理实现：内部会带上系统提示词、历史记忆和可用的 @Tool
        String reply = aiService.chat(memoryId, request.getMessage());
        return ChatResponse.of(reply, memoryId.split(":", 2)[1]);
    }

    @Override
    public StreamChatSession streamChat(Long userId, ChatRequest request) {
        String conversationId = resolveConversationId(request);
        String memoryId = userId + ":" + conversationId;
        // TokenStream 由控制器逐段推送 SSE，记忆与工具调用和同步链路完全一致
        return new StreamChatSession(conversationId, aiService.chatStream(memoryId, request.getMessage()));
    }

    private String resolveConversationId(ChatRequest request) {
        // 若前端没传会话 ID，就自己生成一个随机 ID，用它来做到“连续对话”
        return request.getConversationId() == null || request.getConversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getConversationId();
    }
}
