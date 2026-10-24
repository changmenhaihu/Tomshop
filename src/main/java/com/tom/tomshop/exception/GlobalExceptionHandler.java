package com.tom.tomshop.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *   <li>所有异常出口都返回 {@code Result} 结构；HTTP 状态码只在「未登录 401 / 无权限 403」
 *       两种情况下与业务码对齐，其余一律 200，由业务码驱动前端行为。
 *       这样前端拦截器可以用最简规则处理令牌过期（{@code status === 401} 就触发刷新），
 *       同时又不至于让「库存不足」这类业务失败带上 4xx 状态而被监控误判为接口异常。</li>
 *   <li>Sa-Token 抛出的 {@link NotLoginException} / {@link NotPermissionException}
 *       在此统一转换，业务代码不需要 try-catch 任何鉴权异常。</li>
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
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] {} {} -> code={}, message={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        // 401/403 与 HTTP 语义对齐（前端拦截器据此触发刷新或跳登录），其余保持 200
        HttpStatus status = switch (e.getCode() == null ? 0 : e.getCode()) {
            case 401, 403 -> HttpStatus.valueOf(e.getCode());
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 未登录 / 令牌失效。
     *
     * <p>区分具体原因只用于给出更准确的前端提示（如「账号已在其他设备登录」引导用户重新登录，
     * 而不是让他重试），但不向客户端暴露令牌内容或内部类型名。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e, HttpServletRequest request) {
        String type = e.getType();
        String message;
        if (NotLoginException.TOKEN_TIMEOUT.equals(type)) {
            message = "登录状态已过期，请重新登录";
        } else if (NotLoginException.BE_REPLACED.equals(type)) {
            message = "账号已在其他设备登录";
        } else if (NotLoginException.KICK_OUT.equals(type)) {
            message = "账号已被强制下线";
        } else if (NotLoginException.TOKEN_FREEZE.equals(type)) {
            message = "账号已被临时冻结，请稍后再试";
        } else if (NotLoginException.NO_PREFIX.equals(type)) {
            message = "令牌格式不正确，应为 Authorization: Bearer <token>";
        } else {
            message = "请先登录后再操作";
        }
        log.warn("[未登录] {} {} -> type={}", request.getMethod(), request.getRequestURI(), type);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ResultCode.UNAUTHORIZED, message));
    }

    /** 已登录但缺少所需权限点 / 角色 */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(NotPermissionException e, HttpServletRequest request) {
        // 具体缺哪个权限只进日志：返回给前端会把后台权限模型暴露给普通用户
        log.warn("[无权限] {} {} -> 缺少权限点 {}", request.getMethod(), request.getRequestURI(), e.getPermission());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ResultCode.FORBIDDEN, "无权执行该操作"));
    }

    /** 已登录但缺少所需角色 */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Void>> handleNotRole(NotRoleException e, HttpServletRequest request) {
        log.warn("[无权限] {} {} -> 缺少角色 {}", request.getMethod(), request.getRequestURI(), e.getRole());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ResultCode.FORBIDDEN, "无权执行该操作"));
    }

    /** 其余 Sa-Token 异常（如 JWT 解析失败、账号被踢、二级认证未通过）兜底为 401 */
    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<Result<Void>> handleSaTokenException(SaTokenException e, HttpServletRequest request) {
        log.warn("[鉴权异常] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ResultCode.UNAUTHORIZED));
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
