package com.hape.photogallery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产环境中间件密码强校验（照搬 JwtService 的启动强校验模式）。
 * <p>
 * application-prod.yml 中 RABBIT_PASS / REDIS_PASSWORD 默认空串、RABBIT_USER 默认 admin——
 * 漏配环境变量时会静默以空密码/默认口令连接中间件，这里启动即失败，杜绝静默降级。
 */
@Component
@Profile("prod")
public class ProdSecurityValidator {

    public ProdSecurityValidator(
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${RABBIT_PASS:}") String rabbitPassword,
            @Value("${RABBIT_USER:admin}") String rabbitUser) {
        if (redisPassword == null || redisPassword.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须设置环境变量 REDIS_PASSWORD，例如: export REDIS_PASSWORD=$(openssl rand -base64 24)");
        }
        if (rabbitPassword == null || rabbitPassword.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须设置环境变量 RABBIT_PASS，例如: export RABBIT_PASS=$(openssl rand -base64 24)");
        }
        if ("admin".equalsIgnoreCase(rabbitUser)) {
            throw new IllegalStateException(
                    "生产环境 RABBIT_USER 不能使用默认值 admin，请通过环境变量设置自定义用户名");
        }
    }
}
