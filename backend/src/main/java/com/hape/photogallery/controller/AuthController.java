package com.hape.photogallery.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** Konami 解锁 —— 前端传来 nonce + 按键序列，后端验证（逻辑在 AuthService） */
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> unlock(@RequestBody Object body,
                                                    HttpServletRequest request) {
        // 请求上下文（IP 解析）留在 MVC 层，作为参数传入 service。
        // body 类型为 Object：JSON 数组/字符串/字面量 null 若绑定 Map 会在 Jackson 阶段
        // 抛 HttpMessageNotReadableException（400 但不记失败计数）——下沉到 service
        // 统一 instanceof 校验，畸形输入一律计入 5 次封禁
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
            description = "生成 DB 分享 token（同内容幂等复用，photoIds 白名单 + permission view/download，非法 permission 400）")
    @PostMapping("/api/v1/share/generate")
    public ApiResponse<Map<String, Object>> generateShare(@Valid @RequestBody ShareGenerateRequest req) {
        AuthResult result = authService.generateShare(req.getPhotoIds(), req.getPermission(),
                req.getExpireDays());
        return ApiResponse.success(result.data());
    }

    /** 撤销分享链接（admin；幂等——撤销后旧链接立即 403/404） */
    @Operation(summary = "撤销分享链接",
            description = "按 token 撤销（幂等；不存在 404），撤销后该分享立即失效")
    @PostMapping("/api/v1/share/{token}/revoke")
    public ApiResponse<String> revokeShare(@PathVariable String token) {
        authService.revokeShare(token);
        return ApiResponse.success("分享链接已撤销");
    }
}
