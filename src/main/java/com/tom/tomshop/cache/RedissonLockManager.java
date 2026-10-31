package com.tom.tomshop.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁，当前只服务于「缓存击穿保护」。
 *
 * <p><b>为什么用 Redisson 而不是手写 {@code SET key value NX PX ttl}</b>：
 * 手写版本最容易错的是解锁 —— 直接 {@code DEL} 会把「锁已过期、别人刚拿到的新锁」删掉，
 * 必须用 Lua 比对 token 才能安全释放；再加锁续期、可重入就是完整的一把轮子。
 * Redisson 的 {@link RLock} 已经把这些做对，引入成本只是多一个 jar。
 *
 * <p><b>为什么懒初始化</b>：阶段 0 定下的性质是「应用启动不依赖中间件可用性」。
 * 若在 Bean 构造期就 {@code Redisson.create()}，Redis 未启动会让整个应用起不来。
 * 这里改为首次加锁时才建客户端，失败则永久降级为「无锁」并打一条 warn ——
 * 结果是缓存回源退化为直查数据库，功能仍然可用。
 *
 * <p><b>为什么加锁要带 leaseTime</b>：锁的持有者只做一次数据库查询 + 两次 Redis 写，
 * 正常在毫秒级完成。设置固定租期（默认 10 秒）意味着 Redis 里不会留下永久锁 ——
 * 万一持有者进程被杀，锁会自然过期，而不是把该键的回源永久堵死。
 *
 * @author Tom
 */
@Slf4j
@Component
public class RedissonLockManager implements DisposableBean {

    private static final int DEFAULT_TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_POOL_SIZE = 8;

    private final RedisProperties redisProperties;

    private volatile RedissonClient client;
    private volatile boolean unavailable;

    public RedissonLockManager(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    /**
     * 尝试加锁。
     *
     * @param key          锁键（已含业务前缀）
     * @param waitSeconds  最长等待秒数；0 表示只尝试一次
     * @param leaseSeconds 锁租期秒数，到期自动释放
     * @return true 表示当前线程持锁；false 表示抢锁失败或 Redisson 不可用
     */
    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        RedissonClient redisson = client();
        if (redisson == null) {
            return false;
        }
        try {
            return redisson.getLock(key).tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 恢复中断标记，让上层（Tomcat 线程回收/优雅停机）能感知到
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[缓存] 分布式锁获取异常，按抢锁失败处理：key={}, cause={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 释放锁。仅当当前线程确实持有锁时才解锁，避免 {@code IllegalMonitorStateException} 污染业务日志。
     */
    public void unlock(String key) {
        RedissonClient redisson = client;
        if (redisson == null) {
            return;
        }
        try {
            RLock lock = redisson.getLock(key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            // 解锁失败不影响正确性：锁会在租期结束后自然释放
            log.warn("[缓存] 分布式锁释放异常：key={}, cause={}", key, e.getMessage());
        }
    }

    private RedissonClient client() {
        if (client != null) {
            return client;
        }
        if (unavailable) {
            return null;
        }
        synchronized (this) {
            if (client != null || unavailable) {
                return client;
            }
            try {
                client = Redisson.create(buildConfig());
                log.info("[缓存] Redisson 客户端已就绪，缓存回源启用分布式锁");
            } catch (Exception e) {
                unavailable = true;
                log.warn("[缓存] Redisson 初始化失败，缓存回源降级为无锁直查：{}", e.getMessage());
            }
            return client;
        }
    }

    private Config buildConfig() {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(DEFAULT_POOL_SIZE)
                .setTimeout(timeoutMillis(redisProperties.getTimeout()))
                .setConnectTimeout(timeoutMillis(redisProperties.getConnectTimeout()));

        if (StringUtils.hasText(redisProperties.getUsername())) {
            server.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            server.setPassword(redisProperties.getPassword());
        }
        return config;
    }

    private static int timeoutMillis(Duration timeout) {
        return timeout == null ? DEFAULT_TIMEOUT_MILLIS : Math.toIntExact(timeout.toMillis());
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }
}
