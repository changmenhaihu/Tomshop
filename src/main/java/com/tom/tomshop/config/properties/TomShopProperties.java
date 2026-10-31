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

    /** 多级缓存配置，见 {@code application.yml} 的 {@code tomshop.cache} */
    private Cache cache = new Cache();

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

    /**
     * 多级缓存配置。
     *
     * <p>{@code key-prefix} 里带 {@code v1}：缓存格式一旦不兼容（字段语义变化），
     * 改前缀即可让整批旧数据自然失效，不需要去 Redis 里扫键删除。
     *
     * <p>{@code enabled=false} 时所有缓存读写直通，用于排障与压测基线对比。
     */
    @Data
    public static class Cache {

        /** 总开关。关闭后 {@code getOrLoad} 直接回源，不做任何缓存写入 */
        private boolean enabled = true;

        /** Redis 键前缀，同时也是缓存代际版本 */
        private String keyPrefix = "tomshop:cache:v1:";

        /** 跨实例失效广播频道 */
        private String invalidationChannel = "tomshop:cache:invalidate";

        /** L1 最大条目数，超出后 Caffeine 按 Window TinyLFU 淘汰 */
        private long localMaximumSize = 10_000L;

        /** 未显式指定策略时的 L1 默认 TTL（秒） */
        private long localTtlSeconds = 120L;

        /** 未显式指定策略时的 L2 默认 TTL（秒） */
        private long remoteTtlSeconds = 600L;

        /** 空值缓存 TTL（秒）。太长会让「刚创建的数据」被误判为不存在，因此取小值 */
        private long nullTtlSeconds = 60L;

        /** L2 TTL 随机抖动比例（0.1 表示 ±10%），用于打散过期时刻 */
        private double ttlJitterRatio = 0.1d;

        /** 事务提交后延迟二次删除的间隔（毫秒）；<=0 关闭 */
        private long delayedEvictMillis = 500L;

        /** 缓存回源抢锁的最长等待（秒） */
        private long lockWaitSeconds = 2L;

        /** 缓存回源锁的租期（秒），到期自动释放，防止进程崩溃留下死锁 */
        private long lockLeaseSeconds = 10L;

        /** Redisson 不可用/Redis 报错后的熔断时长（毫秒），期间跳过 L2 */
        private long remoteRetryBackoffMillis = 5000L;
    }
}
