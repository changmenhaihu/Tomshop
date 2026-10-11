package com.tom.tomshop.service.impl;

import com.tom.tomshop.config.properties.TomShopProperties;
import com.tom.tomshop.convert.HealthConverter;
import com.tom.tomshop.dto.HealthDTO;
import com.tom.tomshop.service.HealthService;
import com.tom.tomshop.vo.HealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

/**
 * 健康检查服务实现。
 *
 * <p>探活原则：<b>失败必须被吞掉并降级为状态值，绝不允许把异常抛给探活方</b>。
 * 容器探针拿到 500 与拿到 {@code status=DEGRADED} 的处置方式完全不同：
 * 前者会重启容器，后者只是告警。把控制权交给调用方，是探活接口的基本纪律。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    /** 数据库连接校验超时（秒） */
    private static final int DATABASE_VALID_TIMEOUT_SECONDS = 1;

    /** Redis PING 的正常响应 */
    private static final String REDIS_PONG = "PONG";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final HealthConverter healthConverter;
    private final TomShopProperties properties;
    private final Environment environment;

    @Override
    public HealthVO check() {
        HealthDTO dto = new HealthDTO();
        dto.setApplicationName(environment.getProperty("spring.application.name", "tomshop"));
        dto.setApplicationVersion(properties.getApplicationVersion());
        dto.setActiveProfile(resolveActiveProfiles());
        dto.setJavaVersion(System.getProperty("java.version"));
        dto.setOsName(System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        dto.setServerTime(LocalDateTime.now());
        dto.setDatabaseAvailable(pingDatabase());
        dto.setRedisAvailable(pingRedis());

        HealthVO vo = healthConverter.toVO(dto);
        log.debug("[健康检查] app={}, profile={}, database={}, redis={}, status={}",
                vo.getApplicationName(), vo.getActiveProfile(),
                vo.isDatabaseAvailable(), vo.isRedisAvailable(), vo.getStatus());
        return vo;
    }

    /**
     * 探测 MySQL 连通性。
     *
     * <p>最坏耗时等于 Hikari 的 {@code connection-timeout}（见 application.yml，已刻意设为 5s，
     * 而不是默认的 30s），避免探活接口在数据库宕机时长时间挂起。
     */
    private boolean pingDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_VALID_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("[健康检查] MySQL 不可用：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 探测 Redis 连通性。
     *
     * <p>注意：Redis 客户端连接池（Lettuce）会复用底层连接，
     * 因此这里必须通过 try-with-resources 归还连接，否则连接池会被探活请求逐步耗尽。
     */
    private boolean pingRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return REDIS_PONG.equalsIgnoreCase(connection.ping());
        } catch (Exception e) {
            log.warn("[健康检查] Redis 不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 可能存在多环境同时激活（如 dev + local），统一以逗号拼接返回 */
    private String resolveActiveProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
