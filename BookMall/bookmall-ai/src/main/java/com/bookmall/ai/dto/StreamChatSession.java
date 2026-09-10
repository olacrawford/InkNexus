package com.bookmall.ai.dto;

import dev.langchain4j.service.TokenStream;

/**
 * 流式对话会话：会话 ID 用于前端维持多轮记忆，TokenStream 交给控制器逐段推送 SSE。
 */
public record StreamChatSession(String conversationId, TokenStream tokenStream) {
}
