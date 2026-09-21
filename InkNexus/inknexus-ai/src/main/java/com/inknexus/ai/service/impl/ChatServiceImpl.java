package com.inknexus.ai.service.impl;

import com.inknexus.ai.dto.ChatResponse;
import com.inknexus.ai.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 对话编排实现：经 ChatClient 完成模型调用。每次请求向模型侧传递两类上下文：
 * 1. 会话记忆：advisors 参数携带 {@code userId:conversationId} 形式的会话键，
 *    由 MessageChatMemoryAdvisor 从 Redis 加载/保存该会话的历史；
 * 2. 用户身份：toolContext 携带 userId，工具执行时从中取值，流式/异步线程下不依赖 ThreadLocal。
 */
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatClient chatClient;

    @Override
    public ChatResponse chat(Long userId, String conversationId, String message) {
        String reply = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey(userId, conversationId)))
                .toolContext(Map.of("userId", userId.toString()))
                .call()
                .content();
        return ChatResponse.of(reply, conversationId);
    }

    @Override
    public Flux<String> streamChat(Long userId, String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey(userId, conversationId)))
                .toolContext(Map.of("userId", userId.toString()))
                .stream()
                .content();
    }

    /** 记忆键 = userId:conversationId：userId 前缀实现用户间记忆隔离，防止串到别人的会话历史。 */
    private static String memoryKey(Long userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
