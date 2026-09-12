package com.inknexus.ai.support;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;

/** Result 解包工具：服务间接口统一返回 Result<T>，本类负责取出真正的业务数据 data。 */
public class ResultUtils {

    private ResultUtils() {
        // 工具类不允许实例化，私有构造器即可
    }

    /**
     * 解包 Result 并取出业务数据。
     *
     * @param result 下游服务返回的包装结果
     * @return 真正的业务数据 T
     * @throws BusinessException 当下游调用失败（结果为空或 code 非 200）时抛出
     */
    public static <T> T data(Result<T> result) {
        if (result == null || result.getCode() == null) {
            // 连 Result 都没有，说明下游调用异常，视为系统错误
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (result.getCode() != 200) {
            // 下游返回业务错误，保留原始错误码与信息，交给全局异常处理器统一返回
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}