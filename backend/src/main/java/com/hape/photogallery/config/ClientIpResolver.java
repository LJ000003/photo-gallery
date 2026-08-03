package com.hape.photogallery.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 *
 * 只有在存在受信反代时才信任转发头：默认 `Cf-Connecting-Ip` 由 cloudflared 隧道覆写，
 * 且 app 仅绑定 127.0.0.1 无旁路入口，客户端无法伪造；换成 nginx 等反代时改配置
 * （如 X-Real-IP）即可。未配置受信头 / 头缺失 / 头值非法时一律回退到连接地址
 * （getRemoteAddr），绝不信任可伪造的 X-Forwarded-For。
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final String trustedProxyHeader;

    public ClientIpResolver(@Value("${security.trusted-proxy-header:Cf-Connecting-Ip}") String trustedProxyHeader) {
        this.trustedProxyHeader = trustedProxyHeader == null ? "" : trustedProxyHeader.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (!trustedProxyHeader.isBlank()) {
            String value = request.getHeader(trustedProxyHeader);
            if (value != null && !value.isBlank()) {
                String candidate = value.trim();
                if (isValidIpLiteral(candidate)) {
                    return candidate;
                }
                log.warn("受信头 {} 的值不是合法 IP，忽略并回退连接地址: {}", trustedProxyHeader, candidate);
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isValidIpLiteral(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
