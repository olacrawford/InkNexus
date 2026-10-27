package com.inknexus.book.filter;

import com.inknexus.common.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminOnlyInterceptor 角色拦截规则的单测：GET 放行，非 GET 仅 ADMIN 角色放行，其余返回 403。
 * 角色经由 UserContext 提供（与线上 CurrentUserInterceptor → 本拦截器的执行顺序一致）。
 */
class AdminOnlyInterceptorTest {

    private AdminOnlyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminOnlyInterceptor();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void preHandle_allowsGet_withoutRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/books/page");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void preHandle_rejectsPost_whenRoleMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/books");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void preHandle_rejectsDelete_whenRoleIsUser() throws Exception {
        UserContext.setRole("USER");
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/books/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("无权限执行该操作"));
    }

    @Test
    void preHandle_allowsPost_whenRoleIsAdmin() throws Exception {
        UserContext.setRole("ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/books");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }
}
