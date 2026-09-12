package com.inknexus.ai.support;

import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultUtilsTest {

    @Test
    void data_returnsPayload_whenCodeIs200() {
        Result<String> result = Result.success("hello");
        assertEquals("hello", ResultUtils.data(result));
    }

    @Test
    void data_throwsBusinessException_whenCodeNot200() {
        Result<String> result = Result.fail(404, "图书不存在");
        BusinessException ex = assertThrows(BusinessException.class, () -> ResultUtils.data(result));
        assertEquals(404, ex.getCode());
    }

    @Test
    void data_throwsSystemError_whenResultNull() {
        assertThrows(BusinessException.class, () -> ResultUtils.data(null));
    }
}
