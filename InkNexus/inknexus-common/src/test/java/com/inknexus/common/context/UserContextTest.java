package com.inknexus.common.context;

import com.inknexus.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code UserContext} 的纯单元测试：set/get/require/clear 的基本语义与缺失身份的 401 语义。
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void setAndGet_returnsUserIdAndRole_whenBothPresent() {
        UserContext.setUserId(7L);
        UserContext.setRole("USER");

        assertEquals(7L, UserContext.getUserId());
        assertEquals("USER", UserContext.getRole());
    }

    @Test
    void requireUserId_returnsValue_whenContextPresent() {
        UserContext.setUserId(7L);

        assertEquals(7L, UserContext.requireUserId());
    }

    @Test
    void requireUserId_throws401_whenContextAbsent() {
        BusinessException exception = assertThrows(BusinessException.class, UserContext::requireUserId);

        assertEquals(401, exception.getCode());
    }

    @Test
    void clear_removesBothValues_andAllowsReuse() {
        UserContext.setUserId(7L);
        UserContext.setRole("ADMIN");

        UserContext.clear();

        assertNull(UserContext.getUserId());
        assertNull(UserContext.getRole());

        UserContext.setUserId(8L);
        assertEquals(8L, UserContext.getUserId());
    }

}
