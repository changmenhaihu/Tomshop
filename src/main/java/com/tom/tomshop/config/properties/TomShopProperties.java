package com.tom.tomshop.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TomShop 应用自定义配置，统一以 {@code tomshop.*} 为前缀。
 *
 * <p>原则：应用级可调项一律收敛到这里，禁止在代码中硬编码魔法值；
 * 连接串、密钥等敏感项走环境变量，见 {@code application-dev.yml}。
 *
 * @author Tom
 */
@Data
@Component
@ConfigurationProperties(prefix = "tomshop")
public class TomShopProperties {

    /** 应用版本号，健康检查接口对外暴露 */
    private String applicationVersion = "1.0.0-SNAPSHOT";

    /**
     * 跨域配置。
     *
     * <p>接口前缀不在此声明：{@code @RequestMapping} 需要编译期常量，唯一来源是
     * {@code CommonConstants.API_PREFIX}。配置项与常量双写会导致改了一处、另一处静默失效。
     */
    private Cors cors = new Cors();

    /** 认证配置，见 {@code application.yml} 的 {@code tomshop.auth} */
    private Auth auth = new Auth();

    /**
     * 跨域配置。
     *
     * <p><b>注意</b>：{@link #allowedOrigins} 使用的是 Spring 的
     * {@code allowedOriginPatterns} 语义而非 {@code allowedOrigins}。原因是浏览器规范禁止
     * 「携带凭证的请求」与「通配符 *」同时成立，直接对 {@code allowedOrigins} 传 {@code *}
     * 且 {@link #allowCredentials} 为 true 会在运行时抛 IllegalArgumentException。
     * 生产环境必须把 allowedOrigins 收敛为真实域名列表。
     */
    @Data
    public static class Cors {

        /** 允许的来源，支持通配模式（如 https://*.tomshop.com） */
        private List<String> allowedOrigins = List.of("*");

        private List<String> allowedMethods =
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");

        private List<String> allowedHeaders = List.of("*");

        /** 暴露给浏览器 JS 可读的响应头，阶段 3 起需要读取 Authorization 做 Token 续期 */
        private List<String> exposedHeaders = List.of("Authorization", "Content-Disposition");

        private boolean allowCredentials = true;

        /** 预检请求缓存时间（秒），减少 OPTIONS 请求量 */
        private long maxAge = 3600L;
    }

    /**
     * 认证（双令牌）配置。
     *
     * <p><b>access token 的有效期不在这里</b>：它由 {@code sa-token.timeout} 统一管理，
     * 因为 Sa-Token 会话、JWT 签名与续期逻辑都以该值为准。此处再放一份会形成两个事实来源，
     * 改一处另一处静默失效。响应体中的 {@code expiresIn} 直接取自
     * {@code StpUtil.getTokenTimeout()}，同样是运行时真值。
     */
    @Data
    public static class Auth {

        /** refresh token 有效期（秒），默认 7 天 */
        private long refreshTokenTimeout = 604800L;

        /** refresh token 在 Redis 中的键前缀；jti → userId，用于轮换与主动吊销 */
        private String refreshTokenPrefix = "tomshop:auth:refresh:";
    }
}
