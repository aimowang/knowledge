package org.example.api.config;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.model.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.BindException;

/**
 * 全局异常处理器 — 统一错误响应格式。
 *
 * <p>所有异常返回 {@link ErrorResponse} 格式，包含 status/error/message/path/timestamp。
 * 不向客户端暴露内部实现细节（e.getMessage() 仅记录日志）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, WebRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "参数校验失败", msg, getPath(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e, WebRequest request) {
        log.warn("请求体解析失败", e);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "请求格式错误", "请提供有效的 JSON 请求体", getPath(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e, WebRequest request) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "缺少请求参数", "缺少参数: " + e.getParameterName(), getPath(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, WebRequest request) {
        log.warn("参数约束违反: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "参数约束违反", "请求参数不满足约束条件", getPath(request)));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e, WebRequest request) {
        log.warn("参数绑定失败: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "参数绑定失败", "请求参数格式错误", getPath(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        log.warn("非法参数: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "非法参数", "请检查请求参数", getPath(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, WebRequest request) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "无权限访问", "没有权限访问该资源", getPath(request)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e, WebRequest request) {
        log.warn("认证失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "认证失败", "请提供有效的认证凭证", getPath(request)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e, WebRequest request) {
        log.error("数据完整性异常", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "数据冲突", "请求与现有数据冲突", getPath(request)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, WebRequest request) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(405, "方法不允许", "请求方法 " + e.getMethod() + " 不支持", getPath(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSizeExceeded(MaxUploadSizeExceededException e, WebRequest request) {
        log.warn("上传文件大小超限");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(413, "文件过大", "上传文件大小超过限制", getPath(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e, WebRequest request) {
        // 若响应已提交（如 SSE 流中出错），不修改响应，仅记录日志
        if (request instanceof ServletWebRequest sw) {
            jakarta.servlet.http.HttpServletResponse resp = sw.getResponse();
            if (resp != null && resp.isCommitted()) {
                log.error("SSE/异步流处理异常（响应已提交，无法返回错误）: {}", e.getMessage());
                return null;
            }
        }
        log.error("服务器内部错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "服务器内部错误", "服务器内部错误，请稍后重试", getPath(request)));
    }

    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest sw) {
            return sw.getRequest().getRequestURI();
        }
        return "";
    }
}
