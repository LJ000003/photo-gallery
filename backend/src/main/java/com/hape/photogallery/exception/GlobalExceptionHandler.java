package com.hape.photogallery.exception;

import com.hape.photogallery.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<ApiResponse<?>> handleDuplicate(DuplicateException ex) {
        return ResponseEntity.status(409)
                .body(ApiResponse.error(409, ex.getMessage(), ex.getExisting()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, "文件过大，请上传小于 10MB 的图片"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        // get(0) 曾对「仅 object-level 校验错误」的输入抛 IOBE → 500；先 field 后 object 判空兜底
        var result = ex.getBindingResult();
        String message = result.getFieldErrors().stream().findFirst()
                .map(FieldError::getDefaultMessage)
                .orElseGet(() -> result.getGlobalErrors().stream().findFirst()
                        .map(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "参数校验失败")
                        .orElse("参数校验失败"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, message));
    }

    /** 缺 Content-Type 或类型不匹配的 JSON 请求（裸 curl POST 等）→ 415 而非兜底 500 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(415, "不支持的媒体类型，请使用 application/json"));
    }

    /** 排序字段不在实体属性中（如 /albums/{id}/photos 端点）→ 400 而非 PropertyReferenceException 500 */
    @ExceptionHandler(org.springframework.data.mapping.PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyReference(
            org.springframework.data.mapping.PropertyReferenceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "不支持的排序字段: " + ex.getPropertyName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "请求格式有误，请检查 JSON 格式"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "操作冲突，该数据可能正在被其他内容引用"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "资源不存在"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "缺少必填参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "缺少必填文件参数: " + ex.getRequestPartName()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "请求不是 multipart 格式，缺少上传文件"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "参数格式有误: " + ex.getName()));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        // 曾落入兜底 500「系统繁忙」（如 GET 不存在的 /albums/{id} 详情端点）——方法不支持是客户端错误
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception in request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "系统繁忙，请稍后重试"));
    }
}
