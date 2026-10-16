package com.yx.test.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义业务异常
 * 用于Controller层抛出业务逻辑错误，由GlobalExceptionHandler统一捕获处理
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {
    
    /** 错误码 */
    private int code;
    
    /** 错误消息 */
    private String message;
    
    public BizException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
    }
    
    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    // ==================== 常用业务异常快捷方法 ====================
    
    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }
    
    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }
    
    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }
    
    public static BizException notFound(String message) {
        return new BizException(404, message);
    }
    
    public static BizException internalError(String message) {
        return new BizException(500, message);
    }
}
