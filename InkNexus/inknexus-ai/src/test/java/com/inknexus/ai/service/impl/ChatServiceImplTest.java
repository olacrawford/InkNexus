package com.inknexus.ai.service.impl;

import com.inknexus.ai.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ChatServiceImpl 的单元测试：验证 ChatClient 调用参数——记忆键（userId:conversationId）与 toolContext 用户身份。 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void chat_returnsReplyAndOriginalConversationId_whenCalled() {
        ChatClient.ChatClientRequestSpec spec = mockRequestSpec();
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("你好，需要我帮您搜书或查订单吗？");
        when(chatClient.prompt()).thenReturn(spec);

        ChatResponse response = chatService.chat(7L, "c-1", "你好");

        assertEquals("你好，需要我帮您搜书或查订单吗？", response.getReply());
        assertEquals("c-1", response.getConversationId());
        verify(spec).user("你好");
        verify(spec).toolContext(Map.of("userId", "7"));
        advisorParamIs(spec, ChatMemory.CONVERSATION_ID, "7:c-1");
    }

    @Test
    void streamChat_returnsContentStreamAndPassesMemoryKey_whenCalled() {
        ChatClient.ChatClientRequestSpec spec = mockRequestSpec();
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(spec.stream()).thenReturn(streamSpec);
        Flux<String> deltaStream = Flux.just("推荐", "一本书");
        when(streamSpec.content()).thenReturn(deltaStream);
        when(chatClient.prompt()).thenReturn(spec);

        Flux<String> result = chatService.streamChat(9L, "c-2", "推荐一本书");

        assertSame(deltaStream, result);
        verify(spec).user("推荐一本书");
        verify(spec).toolContext(Map.of("userId", "9"));
        advisorParamIs(spec, ChatMemory.CONVERSATION_ID, "9:c-2");
    }

    /** 桩出 prompt() 的空链：user/advisors/toolContext 均返回自身。 */
    private ChatClient.ChatClientRequestSpec mockRequestSpec() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        when(spec.user(anyString())).thenReturn(spec);
        // advisors 有 Consumer/可变参数/List 三个重载，显式按 Consumer 类型匹配避免歧义
        when(spec.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(spec);
        when(spec.toolContext(anyMap())).thenReturn(spec);
        return spec;
    }

    /** 校验 ChatServiceImpl 传给 advisors(...) 的 Consumer：执行它并确认其向 AdvisorSpec 写入了期望的记忆键。 */
    @SuppressWarnings("unchecked")
    private void advisorParamIs(ChatClient.ChatClientRequestSpec spec, String key, String expectedValue) {
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor =
                ArgumentCaptor.forClass((Class) Consumer.class);
        verify(spec).advisors(captor.capture());
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        captor.getValue().accept(advisorSpec);
        verify(advisorSpec).param(key, expectedValue);
    }
}
