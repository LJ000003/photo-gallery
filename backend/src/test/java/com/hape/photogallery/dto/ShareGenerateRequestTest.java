package com.hape.photogallery.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分享权限字段校验：仅允许 view/download，非法值（如任意字符串）必须被拒绝——
 * 该值会随 viewer JWT 签发，不可放任（缺陷 2 回归测试）。
 */
class ShareGenerateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validPermissions_shouldPass() {
        for (String p : List.of("view", "download")) {
            ShareGenerateRequest req = new ShareGenerateRequest();
            req.setPhotoIds(List.of(1L));
            req.setPermission(p);
            assertThat(validator.validate(req)).as("permission=%s", p).isEmpty();
        }
    }

    @Test
    void invalidPermission_shouldFail() {
        ShareGenerateRequest req = new ShareGenerateRequest();
        req.setPhotoIds(List.of(1L));
        req.setPermission("delete-everything");

        Set<ConstraintViolation<ShareGenerateRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("view"));
    }

    @Test
    void emptyPhotoIds_shouldStillFail() {
        ShareGenerateRequest req = new ShareGenerateRequest();
        req.setPhotoIds(List.of());
        req.setPermission("view");

        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void nullPermission_shouldFail() {
        // @Pattern 对 null 不生效（Bean Validation 语义），必须由 @NotNull 拦下——
        // 显式 "permission": null 曾绕过校验 → NPE 500 / null 权限落库
        ShareGenerateRequest req = new ShareGenerateRequest();
        req.setPhotoIds(List.of(1L));
        req.setPermission(null);

        Set<ConstraintViolation<ShareGenerateRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("不能为空"));
    }
}
