package com.inknexus.book.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOnlyInterceptorTest {

    private AdminOnlyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminOnlyInterceptor();
    }

    @Test
    void preHandle_allowsGet_withoutRoleHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/books/page");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void preHandle_rejectsPost_whenRoleHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/books");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void preHandle_rejectsDelete_whenRoleIsUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/books/1");
        request.addHeader("X-User-Role", "USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("无权限执行该操作"));
    }

    @Test
    void preHandle_allowsPost_whenRoleIsAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/books");
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }
}
