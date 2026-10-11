package com.tom.tomshop.exception;

import com.tom.tomshop.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：由 Service 层主动抛出，经 {@code GlobalExceptionHandler} 统一转换为 {@link com.tom.tomshop.common.result.Result}。
 *
 * <p><b>使用约定</b>：
 * <ul>
 *   <li>属于可预期的业务分支（库存不足、订单状态非法等），只记 warn 日志，不打堆栈。</li>
 *   <li>不要用它包装技术异常（SQLException、IOException 等），那些应向上抛出交由兜底处理并打 error 堆栈。</li>
 *   <li>message 是直接面向用户的提示语，禁止拼接 SQL、堆栈、内部类名等敏感信息。</li>
 * </ul>
 *
 * @author Tom
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，取自 {@link ResultCode} 或模块自定义码段 */
    private final Integer code;

    public BusinessException(String message) {
        this(ResultCode.FAILED, message);
    }

    public BusinessException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
