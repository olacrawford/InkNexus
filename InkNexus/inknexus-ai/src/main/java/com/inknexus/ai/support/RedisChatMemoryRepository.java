package com.inknexus.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 Spring AI 的会话记忆落到 Redis：以 JSON 数组存 {type, text}，不依赖框架内部消息类的序列化格式。
 * 键 {@code chat:memory:{conversationId}}，conversationId 即 {@code userId:conversationId}，实现用户 + 会话两级隔离；
 * 每次写入都以完整列表整体覆盖并刷新 TTL（默认 2 小时，由 ai-assistant.chat.memory-ttl-hours 配置），超期记忆自动淘汰。
 * <p>存储格式与迁移前（LangChain4j 版 RedisChatMemoryStore）完全一致，升级后已有会话的历史记忆可无缝延续。
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
    }

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(redisTemplate.keys(KEY_PREFIX + "*").stream()
                .map(key -> key.substring(KEY_PREFIX.length()))
                .toList());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = redisTemplate.opsForValue().get(key(conversationId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> records = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<Message> messages = new ArrayList<>();
            for (Map<String, Object> record : records) {
                String type = String.valueOf(record.get("type"));
                String text = String.valueOf(record.get("text"));
                switch (type) {
                    case "USER" -> messages.add(new UserMessage(text));
                    case "AI" -> messages.add(new AssistantMessage(text));
                    case "SYSTEM" -> messages.add(new SystemMessage(text));
                    default -> { /* 忽略未知类型，保障向前兼容 */ }
                }
            }
            return messages;
        } catch (Exception e) {
            return new ArrayList<>(); // 解析失败当作无历史，避免拖垮对话
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<Map<String, String>> records = new ArrayList<>();
        for (Message message : messages) {
            // 只保留真实文本，避免依赖 Spring AI 内部消息类的序列化格式
            if (message instanceof UserMessage m) {
                records.add(Map.of("type", "USER", "text", safeText(m.getText())));
            } else if (message instanceof AssistantMessage m) {
                records.add(Map.of("type", "AI", "text", safeText(m.getText())));
            } else if (message instanceof SystemMessage m) {
                records.add(Map.of("type", "SYSTEM", "text", safeText(m.getText())));
            }
        }
        try {
            String json = objectMapper.writeValueAsString(records);
            redisTemplate.opsForValue().set(key(conversationId), json, ttl);
        } catch (Exception e) {
            throw new RuntimeException("写入 AI 会话记忆失败", e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /** 防止文本为 null 时写入 "null"，统一转成空串。 */
    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
