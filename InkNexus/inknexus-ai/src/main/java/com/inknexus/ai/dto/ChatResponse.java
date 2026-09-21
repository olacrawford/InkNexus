package com.inknexus.ai.dto;

import lombok.Data;

/** AI 对话响应体。 */
@Data
public class ChatResponse {

    private String reply;

    /** 本次会话 ID，前端会保存并在下次请求带回，用于连续对话。 */
    private String conversationId;

    public static ChatResponse of(String reply, String conversationId) {
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        response.setConversationId(conversationId);
        return response;
    }
}
