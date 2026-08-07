package com.hape.photogallery.exception;

import com.hape.photogallery.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusiness_shouldReturnCustomStatus() {
        BusinessException ex = new BusinessException(404, "资源不存在");

        ResponseEntity<ApiResponse<Void>> res = handler.handleBusiness(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody().getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void handleBusiness_400() {
        BusinessException ex = new BusinessException(400, "参数错误");
        ResponseEntity<ApiResponse<Void>> res = handler.handleBusiness(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void invalidFileType_shouldBeBusinessException400() {
        // 异常体系统一后：InvalidFileTypeException 是 BusinessException(400)，由 handleBusiness 处理
        InvalidFileTypeException ex = new InvalidFileTypeException("不支持的文件类型");
        assertThat(ex.getStatus()).isEqualTo(400);
        ResponseEntity<ApiResponse<Void>> res = handler.handleBusiness(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("不支持的文件类型");
    }

    @Test
    void handleMaxUploadSize() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024);
        ResponseEntity<ApiResponse<Void>> res = handler.handleMaxUploadSize(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(413);
        assertThat(res.getBody().getMessage()).contains("10MB");
    }

    @Test
    void fileSizeExceeded_shouldBeBusinessException400() {
        // 异常体系统一后：FileSizeExceededException 是 BusinessException(400)，由 handleBusiness 处理
        FileSizeExceededException ex = new FileSizeExceededException("文件过大");
        assertThat(ex.getStatus()).isEqualTo(400);
        ResponseEntity<ApiResponse<Void>> res = handler.handleBusiness(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("文件过大");
    }

    @Test
    void handleValidation() throws Exception {
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                null, new BeanPropertyBindingResult(new Object(), "obj"));
        ex.getBindingResult().addError(new FieldError("obj", "name", "名称不能为空"));

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("名称不能为空");
    }

    @Test
    void handleValidation_onlyObjectError_shouldNotIoBE() throws Exception {
        // 仅 object-level 校验错误（无 field error）：getFieldErrors().get(0) 曾抛 IOBE → 500
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                null, new BeanPropertyBindingResult(new Object(), "obj"));
        ex.getBindingResult().addError(new org.springframework.validation.ObjectError("obj", "对象级错误"));

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("对象级错误");
    }

    @Test
    void handleMediaTypeNotSupported_should415() {
        org.springframework.web.HttpMediaTypeNotSupportedException ex =
                new org.springframework.web.HttpMediaTypeNotSupportedException("application/json");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMediaTypeNotSupported(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void handlePropertyReference_should400WithField() {
        // 构造器要求非 null TypeInformation——用 mock 模拟（仅验证 handler 映射行为）
        org.springframework.data.mapping.PropertyReferenceException ex =
                org.mockito.Mockito.mock(org.springframework.data.mapping.PropertyReferenceException.class);
        org.mockito.Mockito.when(ex.getPropertyName()).thenReturn("foo");

        ResponseEntity<ApiResponse<Void>> res = handler.handlePropertyReference(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("foo");
    }

    @Test
    void handleHttpMessageNotReadable() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleNotReadable(null);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("JSON");
    }

    @Test
    void handleDataIntegrityViolation() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleDataIntegrity(null);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getMessage()).contains("冲突");
    }

    @Test
    void handleException_shouldNotLeakMessage() {
        RuntimeException ex = new RuntimeException("内部数据库密码: secret123");
        ResponseEntity<ApiResponse<Void>> res = handler.handleException(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).doesNotContain("secret123");
        assertThat(res.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }

    @Test
    void handleException_checkException() {
        Exception ex = new Exception("未知错误");
        ResponseEntity<ApiResponse<Void>> res = handler.handleException(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }

    @Test
    void handleMissingParam_shouldReturn400WithParamName() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("swLat", "double");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMissingParam(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("swLat");
    }

    @Test
    void handleMissingPart_shouldReturn400WithPartName() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMissingPart(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("file");
    }

    @Test
    void handleMultipart_shouldReturn400() {
        MultipartException ex = new MultipartException("Current request is not a multipart request");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMultipart(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("multipart");
    }

    @Test
    void handleTypeMismatch_shouldReturn400WithParamName() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "tagIds", null, null);

        ResponseEntity<ApiResponse<Void>> res = handler.handleTypeMismatch(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).contains("tagIds");
    }

    /** 方法不支持（如 GET 不存在的端点）此前落兜底 500——405 是客户端错误，不应 500 */
    @Test
    void handleMethodNotSupported_shouldReturn405() {
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMethodNotSupported(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(405);
        assertThat(res.getBody().getCode()).isEqualTo(405);
    }
}
