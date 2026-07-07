package com.hape.photogallery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
    void handleInvalidFileType() {
        InvalidFileTypeException ex = new InvalidFileTypeException("不支持的文件类型");
        ResponseEntity<ApiResponse<Void>> res = handler.handleInvalidFileType(ex);
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
    void handleFileSizeExceeded() {
        FileSizeExceededException ex = new FileSizeExceededException("文件过大");
        ResponseEntity<ApiResponse<Void>> res = handler.handleFileSizeExceeded(ex);
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
}
