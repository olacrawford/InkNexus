package com.inknexus.ai.service;

import com.inknexus.ai.dto.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务接口：编排一次对话的模型调用。
 * conversationId 由控制器归一（带回则沿用、缺失则新生成）后传入，
 * 服务只负责 ChatClient 调用与记忆键组装，感知不到 HTTP 细节。
 */
public interface ChatService {

    /** 发起一次 AI 对话，返回完整回复（同步）。 */
    ChatResponse chat(Long userId, String conversationId, String message);

    /** 发起一次流式 AI 对话，返回回复内容的增量片段流（懒执行，订阅后才开始生成）。 */
    Flux<String> streamChat(Long userId, String conversationId, String message);
}
