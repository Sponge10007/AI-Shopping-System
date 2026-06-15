package com.aishop.common.exception;

import com.aishop.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 捕获所有 Controller 层抛出的异常，统一返回 JSON 格式的 ApiResponse。
 *
 * 异常映射规则：
 * - BusinessException → 根据 ErrorCode 映射到对应 HTTP 状态码
 * - MethodArgumentNotValidException → 400（@Valid 校验失败）
 * - MissingServletRequestPartException → 400（缺少请求参数）
 * - MissingServletRequestParameterException → 400（缺少请求参数）
 * - MissingRequestHeaderException → 403（缺少内部服务令牌等必需请求头）
 * - MultipartException → 400（文件上传异常，如空文件）
 * - MethodArgumentTypeMismatchException → 400（参数类型不匹配）
 * - HttpMessageNotReadableException → 400（请求体不可读）
 * - Exception → 500（未预期的异常，记录完整堆栈）
 *
 * 修复记录（2026-06-08 v2）：
 * - 增加 MethodArgumentTypeMismatchException 处理（如 @AuthenticationPrincipal 类型转换失败）
 * - 增加 HttpMessageNotReadableException 处理（请求体 JSON 格式错误）
 * - 增加 MissingServletRequestParameterException 处理
 * - 未捕获异常增加完整堆栈日志
 *
 * 修复记录（2026-06-08 v3）：
 * - 增加 MissingRequestHeaderException 处理，返回 403 FORBIDDEN
 *   解决 InternalProductController 未携带 X-Internal-Token 时返回 500 的问题
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        log.debug("业务异常: code={}, message={}", code, exception.getMessage());
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(code.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.debug("参数校验失败: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(), message));
    }

    /**
     * 捕获缺少请求参数的异常（如 @RequestPart 缺少 multipart 部分）
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPartException(MissingServletRequestPartException exception) {
        log.debug("缺少请求参数: {}", exception.getRequestPartName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(),
                        "缺少请求参数: " + exception.getRequestPartName()));
    }

    /**
     * 捕获缺少请求参数的异常（如 @RequestParam 缺少参数）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParamException(MissingServletRequestParameterException exception) {
        log.debug("缺少请求参数: {}", exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(),
                        "缺少请求参数: " + exception.getParameterName()));
    }

    /**
     * 捕获缺少必需请求头的异常（如 X-Internal-Token）
     * 返回 403 Forbidden，与 InternalTokenFilter 的语义一致
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeaderException(MissingRequestHeaderException exception) {
        log.warn("缺少必需请求头: {}", exception.getHeaderName());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN.name(),
                        "缺少必需请求头: " + exception.getHeaderName()));
    }

    /**
     * 捕获文件上传异常（如空文件、文件大小超限等）
     * 注意：文件大小超限由 Spring 的 MultipartException 抛出
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        String message = exception.getMessage();
        log.debug("文件上传异常: {}", message);
        if (message != null && message.contains("size")) {
            return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
                    .body(ApiResponse.fail(ErrorCode.FILE_TOO_LARGE.name(), "文件大小超过限制"));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(), "文件上传失败: " + exception.getMessage()));
    }

    /**
     * 捕获参数类型不匹配异常（如 @AuthenticationPrincipal 类型转换失败）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        log.warn("参数类型不匹配: name={}, value={}, requiredType={}",
                exception.getName(), exception.getValue(), exception.getRequiredType());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(),
                        "参数类型不匹配: " + exception.getName()));
    }

    /**
     * 捕获请求体不可读异常（JSON 格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.debug("请求体不可读: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_ARGUMENT.name(), "请求体格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        // 记录完整堆栈，便于排查 500 错误
        log.error("未捕获的异常: {}", exception.getMessage(), exception);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.name(), exception.getMessage()));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
