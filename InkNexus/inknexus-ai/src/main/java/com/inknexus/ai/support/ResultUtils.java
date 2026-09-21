package com.inknexus.ai.support;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.result.Result;

/**
 * Result 解包工具：服务间接口统一返回 Result<T>，本类负责取出真正的业务数据 data。
 * 供 @Tool 工具层使用——模型只认得业务数据本身，包装头需在这里剥掉（约定 code==200 即成功）。
 */
public class ResultUtils {

    private ResultUtils() {
    }

    /** 解包 Result 取业务数据；结果为空或 code 非 200 时抛 BusinessException。 */
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