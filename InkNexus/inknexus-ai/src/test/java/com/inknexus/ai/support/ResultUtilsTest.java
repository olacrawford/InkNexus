package com.inknexus.ai.support;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ResultUtils 的单元测试：验证 Result 按 code 解包与失败抛 BusinessException 的行为。 */
class ResultUtilsTest {

    @Test
    void data_returnsPayload_whenCodeIs200() {
        Result<String> result = Result.success("hello");
        assertEquals("hello", ResultUtils.data(result));
    }

    @Test
    void data_throwsBusinessException_whenCodeNot200() {
        Result<String> result = Result.fail(ErrorCode.NOT_FOUND);
        BusinessException ex = assertThrows(BusinessException.class, () -> ResultUtils.data(result));
        assertEquals(404, ex.getCode());
    }

    @Test
    void data_throwsSystemError_whenResultNull() {
        assertThrows(BusinessException.class, () -> ResultUtils.data(null));
    }
}
