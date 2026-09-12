package com.inknexus.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** AI 对话请求体。@Data 是 Lombok 注解，自动生成 getter/setter/toString，省去样板代码。 */
@Data
public class ChatRequest {

    /** 用户发送的文本消息。@NotBlank 表示不能为空（也不能是纯空格）。 */
    @NotBlank(message = "消息不能为空")
    private String message;

    /** 可选：会话ID。缺省由服务端生成，用于 Redis 会话记忆隔离。 */
    private String conversationId;
}
