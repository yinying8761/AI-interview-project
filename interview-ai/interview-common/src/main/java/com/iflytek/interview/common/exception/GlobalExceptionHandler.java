package com.iflytek.interview.common.exception;

import com.iflytek.interview.common.response.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：统一处理所有 Controller 抛出的异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 请求体 JSON 格式错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误", e);
        return Result.error(400, "请求体格式错误");
    }

    /**
     * 缺少必需的请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少请求参数: " + e.getParameterName());
    }

    /**
     * 参数类型转换失败（如 /api/scenarios/abc，id 转不成 Long）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}", e.getName());
        return Result.error(400, "参数类型错误: " + e.getName());
    }

    /**
     * 方法参数校验失败（@PathVariable / @RequestParam 上的校验注解触发）
     * 注意：这只对"方法参数级别"的校验；@RequestBody 字段走 MethodArgumentNotValidException
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("方法参数校验失败: {}", e.getMessage());
        return Result.error(400, "方法参数校验失败，请检查路径或查询参数");
    }

    /**
     * 约束违规（Service 层 @Validated 显式校验等场景触发）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleViolation(ConstraintViolationException e) {
        log.warn("约束违规: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 授权异常（方法级 @PreAuthorize 权限不足）
     * 语义是"已登录但没权限"，HTTP 应为 403（401 是"未登录"）。
     * Security 6.3+ 的 @PreAuthorize 授权失败抛 AuthorizationDeniedException 走这里；
     * 过滤器链层面的 403 走 SecurityConfig 的 accessDeniedHandler。
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public Result<Void> handleAuthorizationDeniedException(AuthorizationDeniedException e) {
        log.warn("授权异常: message={}", e.getMessage());
        return Result.error(403, "不具备相应的权限");
    }

    /**
     * 兜底：其他所有异常（不要直接把堆栈抛给用户）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}