package com.tom.tomshop.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 *
 * <p>序列化选型说明（<b>生产环境最容易踩的坑</b>）：
 * <ul>
 *   <li>默认的 {@code JdkSerializationRedisSerializer} 会把类名与序列化 ID 写进 Redis，
 *       Key 呈现为不可读的二进制，且类结构变更后旧数据直接反序列化失败。本类改用 Jackson。</li>
 *   <li>Key / HashKey 用 {@code StringRedisSerializer}，保证 {@code redis-cli} 里 Key 可读、可手查。</li>
 *   <li>Value 用 {@code GenericJackson2JsonRedisSerializer} 并开启 default typing：
 *       写入时附加 {@code @class} 字段，读取时才能还原为原始类型，而不是退化成 LinkedHashMap
 *       （后者会在 {@code (Product) redisTemplate.opsForValue().get(key)} 处抛 ClassCastException）。</li>
 *   <li>{@code activateDefaultTyping(..., NON_FINAL)} 让 final 类型（String、Integer 等）
 *       保持轻量，只对自定义对象附加类型信息。</li>
 * </ul>
 *
 * <p><b>安全提示</b>：{@code LaissezFaireSubTypeValidator} 不限制反序列化类型，
 * 因此 Redis 必须处于可信内网、且不允许外部写入。这是 Spring Data Redis 官方文档推荐的
 * 默认配置方式；若后续 Redis 暴露面扩大，应替换为白名单式的 {@code PolymorphicTypeValidator}。
 *
 * @author Tom
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 专用 ObjectMapper。
     *
     * <p>刻意不复用 {@code JsonUtils} 的 mapper：Web 层的 Long → String 规则（雪花 ID 精度保护）
     * 不应污染缓存，缓存中保留原始数值类型才能保证反序列化回实体时类型正确。
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持 LocalDateTime 等 JSR-310 类型，否则缓存实体时会抛 InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());
        // 时间以 ISO 字符串存储，便于人眼排查；不使用默认的时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 允许序列化 private 字段，兼容只有 getter 或 Lombok 生成的实体
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 开启多态类型信息，写入 @class，读取时还原真实类型
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
