package com.tom.tomshop.exception;

import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有异常出口都返回 {@code Result}，HTTP 状态码统一保持 200，由业务码驱动前端行为——
 *       避免前端既解析 HTTP 状态又解析响应体。（阶段 3 的 401 会额外保留 HTTP 401，
 *       以便前端拦截器区分「需刷新 Token」与其他业务失败。）</li>
 *   <li>参数校验类异常记 warn，业务异常记 warn，未预期异常记 error 并保留堆栈。</li>
 *   <li>日志中始终带 uri + method，便于线上定位；不把堆栈信息返回给客户端。</li>
 * </ul>
 *
 * @author Tom
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期分支，不打印堆栈 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] {} {} -> code={}, message={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    /** 表单/Query 参数绑定对象校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    /** 方法级 @Validated 校验失败（路径变量、单个请求参数） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e,
                                               HttpServletRequest request) {
        String message = "缺少必填参数：" + e.getParameterName();
        log.warn("[参数缺失] {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    /** 参数类型不匹配，例如路径变量应为数字却传了字符串 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "参数类型不正确：" + e.getName();
        log.warn("[参数类型错误] {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    /** 请求体不可读：JSON 语法错误、枚举值非法等 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[请求体解析失败] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID, "请求参数格式不正确，请检查请求体");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                 HttpServletRequest request) {
        log.warn("[请求方法不支持] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
    }

    /** 静态资源 / 接口不存在（Spring Boot 3.2+ 默认抛出 NoResourceFoundException） */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[资源不存在] {} {}", request.getMethod(), request.getRequestURI());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /** 兜底：未预期异常。对外只暴露通用文案，堆栈留在服务端日志 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
