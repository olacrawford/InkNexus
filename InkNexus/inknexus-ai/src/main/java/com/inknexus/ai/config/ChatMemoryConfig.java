package com.inknexus.ai.config;

import com.inknexus.ai.support.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 会话记忆配置：把 Spring AI 的记忆存储接到 Redis，并按“消息窗口”限制上下文长度。
 * <p>conversationId 由 ChatServiceImpl 拼成 {@code userId:conversationId}：userId 前缀保证不同用户的
 * 记忆互不可见（记忆隔离），conversationId 保证同一用户可有多条独立会话。
 */
@Configuration
public class ChatMemoryConfig {

    /** 每个会话最多保留最近的 20 条消息。 */
    private static final int MAX_MESSAGES = 20;

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate redisTemplate,
                                                               @Value("${ai-assistant.chat.memory-ttl-hours:2}") long ttlHours) {
        return new RedisChatMemoryRepository(redisTemplate, Duration.ofHours(ttlHours));
    }

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}
