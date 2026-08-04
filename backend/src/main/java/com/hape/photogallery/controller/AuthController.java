package com.hape.photogallery.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.config.ClientIpResolver;
import com.hape.photogallery.dto.ShareGenerateRequest;
import com.hape.photogallery.service.AuthService;
import com.hape.photogallery.service.AuthService.AuthResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver ipResolver;

    public AuthController(AuthService authService, ClientIpResolver ipResolver) {
        this.authService = authService;
        this.ipResolver = ipResolver;
    }

    /** 获取一次性 nonce，60 秒有效 */
    @Operation(summary = "获取解锁一次性 nonce", description = "60s 有效，一次性消费（Challenge-Response 第一步）")
    @GetMapping("/api/v1/auth/challenge")
    public ApiResponse<Map<String, String>> challenge() {
        return ApiResponse.success(Map.of("nonce", authService.generateNonce()));
    }

    /** Konami 解锁 —— 前端传来 nonce + 按键序列，后端验证（逻辑在 AuthService，P4-#48④） */
    @Operation(summary = "Konami 解锁",
            description = "提交 nonce + 12 键序列，后端比对配置中的序列；错误计数 5 次封禁 15 分钟；签发 24h admin JWT",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                            description = "nonce 失效或按键不完整"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                            description = "序列不正确"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                            description = "IP 已被封禁（15 分钟）")
            })
    @PostMapping("/api/v1/auth/unlock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unlock(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        // 请求上下文（IP 解析）留在 MVC 层，作为参数传入 service
        String ip = ipResolver.resolve(request);
        AuthResult result = authService.unlock(ip, body);
        if (result.data() != null) {
            return ResponseEntity.ok(ApiResponse.success(result.data()));
        }
        return ResponseEntity.status(result.status())
                .body(ApiResponse.error(result.status(), result.message()));
    }

    /** 管理员生成分享链接 */
    @Operation(summary = "生成分享链接",
            description = "签发 7 天 viewer JWT（photoIds 白名单 + permission view/download，非法 permission 400）")
    @PostMapping("/api/v1/share/generate")
    public ApiResponse<Map<String, Object>> generateShare(@Valid @RequestBody ShareGenerateRequest req) {
        AuthResult result = authService.generateShare(req.getPhotoIds(), req.getPermission(),
                req.getExpireDays());
        return ApiResponse.success(result.data());
    }
}
