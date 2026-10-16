package com.yx.test.common;

import lombok.Data;

/**
 * 统一API响应结果封装 (RESTful)
 */
@Data
public class Result<T> {
    
    /** 状态码: 200-成功, 400-客户端错误, 500-服务器错误 */
    private int code;
    
    /** 消息描述 */
    private String message;
    
    /** 响应数据 */
    private T data;
    
    public Result() {}
    
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    // ==================== 成功响应 ====================
    
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }
    
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }
    
    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }
    
    // ==================== 失败响应 ====================
    
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
    
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
    
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }
    
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
    
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }
    
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }
}
