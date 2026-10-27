package com.inknexus.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CurrentUserInterceptor} 的纯单元测试：身份写入、线程复用残留防护、
 * 非法头值容错与异常路径下 afterCompletion 的清理保证。
 */
class CurrentUserInterceptorTest {

    private final CurrentUserInterceptor interceptor = new CurrentUserInterceptor();

    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void preHandle_setsContext_whenTrustedHeadersPresent() throws Exception {
        request.addHeader(UserContext.USER_ID_HEADER, "7");
        request.addHeader(UserContext.USER_ROLE_HEADER, "ADMIN");

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertEquals(7L, UserContext.getUserId());
        assertEquals("ADMIN", UserContext.getRole());
    }

    @Test
    void preHandle_clearsResidualContext_whenHeadersAbsent() throws Exception {
        // 模拟线程复用残留：上一个请求的上下文还在
        UserContext.setUserId(99L);
        UserContext.setRole("ADMIN");

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(UserContext.getUserId());
        assertNull(UserContext.getRole());
    }

    @Test
    void preHandle_keepsContextEmpty_whenUserIdHeaderIsNotANumber() throws Exception {
        request.addHeader(UserContext.USER_ID_HEADER, "not-a-number");

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(UserContext.getUserId());
        assertNull(UserContext.getRole());
    }

    @Test
    void afterCompletion_clearsContext_evenWhenHandlerThrew() {
        UserContext.setUserId(7L);
        UserContext.setRole("USER");

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        assertNull(UserContext.getUserId());
        assertNull(UserContext.getRole());
    }

}
