package com.yx.test.config;

import com.yx.test.common.BizException;
import com.yx.test.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常处理（@Valid/@Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        StringBuilder errorMsg = new StringBuilder();
        e.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errorMsg.append(fieldError.getField())
                        .append(": ")
                        .append(fieldError.getDefaultMessage())
                        .append("; ");
            }
        });
        String message = errorMsg.length() > 0 ? errorMsg.toString() : "参数校验失败";
        log.warn("参数校验异常: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 绑定异常处理
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        StringBuilder errorMsg = new StringBuilder();
        e.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errorMsg.append(fieldError.getField())
                        .append(": ")
                        .append(fieldError.getDefaultMessage())
                        .append("; ");
            }
        });
        String message = errorMsg.length() > 0 ? errorMsg.toString() : "参数绑定失败";
        log.warn("参数绑定异常: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 路径 / 查询参数类型不对。
     *
     * <p>例：{@code GET /api/executions/abc/ai-analysis}，而路径上的 id 要求是数字。</p>
     *
     * <p>不加这个处理器的话会落到下面的 RuntimeException / Exception 兜底，
     * 变成「HTTP 500 + 系统异常，请联系管理员」—— 明明是调用方参数写错，
     * 却报成服务端故障，排查时很容易被带偏。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        String need = requiredType == null ? "正确类型" : requiredType.getSimpleName();
        String message = String.format("参数 %s 类型不正确，需要 %s", e.getName(), need);
        log.warn("参数类型不匹配: {} = {}", e.getName(), e.getValue());
        return Result.badRequest(message);
    }

    /**
     * 运行时异常处理
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        // 完整堆栈只写进服务端日志（自己排查用）
        log.error("运行时异常", e);
        // 返回给前端的只能是一句模糊提示。
        // 原来这里拼了 e.getMessage()，会把内部异常信息直接送到调用方，
        // 比如 MyBatis 报的 "Unknown column 'xxx' in 'field list'"、
        // 或数据库报的表名/字段名，相当于把后端结构免费告诉攻击者。
        // 这里与下面 handleException 兜底的写法保持一致。
        return Result.error("系统异常，请联系管理员");
    }

    /**
     * 资源不存在异常处理（访问了不存在的路径）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.error(404, "接口不存在，请检查请求路径");
    }

    /**
     * 通用异常处理（兜底）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}
