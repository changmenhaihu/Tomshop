package com.tom.tomshop.config;

import com.tom.tomshop.cache.CacheInvalidationListener;
import com.tom.tomshop.config.properties.TomShopProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多级缓存的容器与线程配置。
 *
 * <p>Caffeine 实例不在这里声明：它的每个条目都带独立 TTL 策略，
 * 需要与 {@code MultiLevelCache} 内部的 {@code LocalEntry} 类型绑定，
 * 放在组件内部构建比暴露一个 {@code Cache<String, ?>} Bean 更内聚。
 *
 * @author Tom
 */
@Slf4j
@Configuration
public class CacheConfig {

    /**
     * 失效广播订阅容器。
     *
     * <p>容器在 Redis 不可用时会自行重试连接，不影响应用启动 ——
     * 这与阶段 0 定下的「启动不依赖中间件」一致。
     */
    @Bean
    public RedisMessageListenerContainer cacheInvalidationContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationListener listener,
            TomShopProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener,
                new ChannelTopic(properties.getCache().getInvalidationChannel()));
        log.info("[缓存] 失效广播订阅已注册：channel={}", properties.getCache().getInvalidationChannel());
        return container;
    }

    /**
     * 延迟二次删除用的调度器。
     *
     * <p>刻意只用一个守护线程：任务体只是两次 Redis 操作，彼此独立且量很小
     * （只在写操作提交后使用）。守护线程保证它不会阻止 JVM 退出。
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService cacheEvictionScheduler() {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "tomshop-cache-evict-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
