package com.tom.tomshop.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import com.tom.tomshop.security.RedisCompatibleSaTokenDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 覆盖 Sa-Token 默认的 Redis 会话存储实现。
 *
 * <p>{@code sa-token-redis-template} 通过自动装配注册了
 * {@link SaTokenDaoForRedisTemplate}，但它的 {@code update} 依赖 Redis 6.0 的
 * {@code KEEPTTL} 语法。本机是 Redis 5.0，于是这里注册一个子类实现并用
 * {@code @Primary} 抢占注入点 —— 自动装配的 Bean 仍会创建（不产生副作用），
 * 但 Sa-Token 的 {@code SaBeanInject} 会拿到本类。
 *
 * <p>之所以不直接 {@code spring.autoconfigure.exclude} 排除自动装配：那条路径一旦
 * 类名写错，启动时才会报错，而 {@code @Primary} 是编译期就能验证的。
 *
 * <p>Redis 升到 6.0+ 之后，删除本类与 {@link RedisCompatibleSaTokenDao} 即可。
 *
 * @author Tom
 */
@Slf4j
@Configuration
public class SaTokenRedisConfig {

    @Bean
    @Primary
    public SaTokenDao saTokenDao(RedisConnectionFactory connectionFactory) {
        RedisCompatibleSaTokenDao dao = new RedisCompatibleSaTokenDao();
        dao.init(connectionFactory);
        log.info("[鉴权] Sa-Token 会话存储已切换为 Redis 5 兼容实现");
        return dao;
    }
}
