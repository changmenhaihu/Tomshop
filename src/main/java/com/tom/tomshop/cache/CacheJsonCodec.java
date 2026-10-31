package com.tom.tomshop.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 缓存值的 JSON 编解码器。
 *
 * <p><b>为什么不复用 {@code RedisConfig} 里的 RedisTemplate</b>：
 * 那套配置开启了 default typing（写入 {@code @class}），让 Redis 里能还原任意对象。
 * 但类型信息会带来两个问题：
 * <ol>
 *   <li>类名进入缓存值，重命名包/类后旧缓存无法反序列化；</li>
 *   <li>{@code List.of()} / {@code stream().toList()} 产生的不可变集合实现类
 *       没有 Jackson 可用的创建器，读回时会直接抛异常。</li>
 * </ol>
 * 缓存读取方本来就知道目标类型（{@link MultiLevelCache#getOrLoad} 的 {@code TypeReference}
 * 参数），因此存纯 JSON 更稳：缓存格式只依赖字段，不依赖实现类。
 *
 * <p>本 mapper 刻意<b>不</b>复用 Web 层的 {@code JsonUtils}：那里把 {@code Long}
 * 序列化为字符串（雪花 ID 前端精度保护），缓存中应保留原始数值类型。
 *
 * @author Tom
 */
@Slf4j
@Component
public class CacheJsonCodec {

    private final ObjectMapper objectMapper;

    public CacheJsonCodec() {
        this.objectMapper = new ObjectMapper()
                // LocalDateTime 等 JSR-310 类型必须以 ISO 字符串存储，否则嵌套数组不可读也不稳定
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // VO 增加字段后旧缓存仍可读，避免一次发布就把整个缓存层打成全量未命中
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存序列化失败：" + value.getClass().getName(), e);
        }
    }

    /**
     * 解码缓存值。
     *
     * <p>返回 {@code null} 表示「解码失败」而不是「值为 null」——后者由空值标记单独表达。
     * 解码失败按未命中处理：宁可回源一次，也不要让一条脏缓存把接口永久打死。
     */
    public <T> T decode(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[缓存] 反序列化失败，按未命中处理：{}", e.getOriginalMessage());
            return null;
        }
    }
}
