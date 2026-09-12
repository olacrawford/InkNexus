package com.inknexus.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 LangChain4j 的 ChatMemoryStore 落到 Redis。
 * 以 JSON 数组存储 {type, text}，type 取 USER / AI / SYSTEM，避免依赖内部消息类序列化。
 */
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
    }

    /** 拼接 Redis 键名：chat:memory:{用户:会话}，保证每个会话独立。 */
    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    /** 读取某会话的历史消息：从 Redis 取 JSON，反序列化成 LangChain4j 的 ChatMessage 列表。 */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(key(memoryId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>(); // 没有历史就返回空列表
        }
        try {
            // 存的是一组 {type, text}，这里还原成 UserMessage / AiMessage / SystemMessage
            List<Map<String, Object>> records = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<ChatMessage> messages = new ArrayList<>();
            for (Map<String, Object> record : records) {
                String type = String.valueOf(record.get("type"));
                String text = String.valueOf(record.get("text"));
                switch (type) {
                    case "USER" -> messages.add(UserMessage.from(text));
                    case "AI" -> messages.add(AiMessage.from(text));
                    case "SYSTEM" -> messages.add(SystemMessage.from(text));
                    default -> { /* 忽略未知类型，保障向前兼容 */ }
                }
            }
            return messages;
        } catch (Exception e) {
            return new ArrayList<>(); // 解析失败当作无历史，避免拖垮对话
        }
    }

    /** 保存某会话的历史消息：把 ChatMessage 转成 {type, text} 的 JSON 写入 Redis，并带上 TTL。 */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<Map<String, String>> records = new ArrayList<>();
        for (ChatMessage message : messages) {
            // 只保留真实文本，避免依赖 LangChain4j 内部消息类的序列化格式
            if (message instanceof UserMessage m) {
                records.add(Map.of("type", "USER", "text", safeText(m.singleText())));
            } else if (message instanceof AiMessage m) {
                records.add(Map.of("type", "AI", "text", safeText(m.text())));
            } else if (message instanceof SystemMessage m) {
                records.add(Map.of("type", "SYSTEM", "text", safeText(m.text())));
            }
        }
        try {
            String json = objectMapper.writeValueAsString(records);
            // 写入 Redis 并设置过期时间，超时自动删除，避免会话历史永久占用内存
            redisTemplate.opsForValue().set(key(memoryId), json, ttl);
        } catch (Exception e) {
            throw new RuntimeException("写入 AI 会话记忆失败", e);
        }
    }

    /** 删除某会话的历史记录（例如新对话 / 清理时调用）。 */
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(key(memoryId));
    }

    /** 防止文本为 null 时写入 "null"，统一转成空串。 */
    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
