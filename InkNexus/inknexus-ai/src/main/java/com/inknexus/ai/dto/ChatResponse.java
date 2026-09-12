package com.inknexus.ai.dto;

import lombok.Data;

/** AI 对话响应体。 */
@Data
public class ChatResponse {

    /** AI 返回的文本内容（图书推荐 / 订单信息等）。 */
    private String reply;

    /** 本次会话 ID，前端会保存并在下次请求带回，用于连续对话。 */
    private String conversationId;

    /**
     * 静态工厂方法：快速构造一个响应对象。
     * 用 ChatResponse.of(...) 调用，无需 new + 多次 set。
     */
    public static ChatResponse of(String reply, String conversationId) {
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        response.setConversationId(conversationId);
        return response;
    }
}
