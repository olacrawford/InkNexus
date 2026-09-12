package com.inknexus.ai.config;

import com.inknexus.ai.support.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** 会话记忆配置：把 LangChain4j 的记忆存储接到 Redis，并按“消息窗口”限制上下文长度。 */
@Configuration
public class ChatMemoryConfig {

    /** 每个会话最多保留最近的 20 条消息。 */
    private static final int MAX_MESSAGES = 20;

    /** 自己实现 ChatMemoryStore：把对话历史以 JSON 形式存储到 Redis。 */
    @Bean
    public ChatMemoryStore redisChatMemoryStore(StringRedisTemplate redisTemplate,
                                                @Value("${ai-assistant.chat.memory-ttl-hours:2}") long ttlHours) {
        // 过期时间从配置读，默认 2 小时
        return new RedisChatMemoryStore(redisTemplate, Duration.ofHours(ttlHours));
    }

    /** 每个 memoryId（= 用户:会话）对应一个 MessageWindowChatMemory，可记忆多轮对话。 */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(store)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}
