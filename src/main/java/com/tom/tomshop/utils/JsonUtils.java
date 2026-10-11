package com.tom.tomshop.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.exception.BusinessException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * JSON 工具类。
 *
 * <p><b>单一配置源</b>：{@link #applyTo(Jackson2ObjectMapperBuilder)} 同时被本类与
 * {@code JacksonConfig} 调用，保证「Web 层 HTTP 序列化」与「工具类手动序列化」
 * （阶段 3 的 Redis Token 存储、阶段 5 的 MQ 消息体）使用完全一致的规则，
 * 避免出现两套 Jackson 行为导致的诡异不一致。
 *
 * <p>序列化约定：
 * <ul>
 *   <li>包装类型 {@code Long} → 字符串：Java 雪花 ID 为 19 位，超出 JS
 *       {@code Number.MAX_SAFE_INTEGER}（2^53-1，约 16 位），直接传数字前端会静默丢精度，
 *       导致「看到的是 A 的 ID，提交回去变成 B」这类极难排查的数据错乱。</li>
 *   <li>基本类型 {@code long} / {@code int} → 保持数字：计数、页码等非 ID 语义字段不该变成字符串，
 *       因此 {@code PageResult} 的 total/pages 用 {@code long} 而非 {@code Long}。</li>
 *   <li>时间类型统一 {@code yyyy-MM-dd HH:mm:ss}，时区 GMT+8，禁用时间戳数组形式。</li>
 * </ul>
 *
 * @author Tom
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /** 工具类专用 mapper，与 Web 层共用同一套 {@link #applyTo} 规则 */
    private static final ObjectMapper MAPPER = createObjectMapper();

    /**
     * 构建符合 TomShop 约定的 {@link ObjectMapper}。
     */
    public static ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        applyTo(builder);
        return builder.build();
    }

    /**
     * 将 TomShop 的 Jackson 规则应用到给定 builder。
     * <p>Web 层由 {@code JacksonConfig} 注册为 {@code Jackson2ObjectMapperBuilderCustomizer} 调用本方法。
     */
    public static void applyTo(Jackson2ObjectMapperBuilder builder) {
        builder.timeZone(TimeZone.getTimeZone(CommonConstants.TIME_ZONE))
                .locale(Locale.CHINA)
                .createXmlMapper(false)
                .failOnUnknownProperties(false)
                .failOnEmptyBeans(false)
                .serializationInclusion(JsonInclude.Include.ALWAYS)
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                // 雪花 ID 精度保护：仅包装类型 Long 转字符串，基本类型 long 不受影响
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class,
                        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(CommonConstants.DATE_TIME_PATTERN)))
                .serializerByType(LocalDate.class,
                        new LocalDateSerializer(DateTimeFormatter.ofPattern(CommonConstants.DATE_PATTERN)))
                .serializerByType(LocalTime.class,
                        new LocalTimeSerializer(DateTimeFormatter.ofPattern(CommonConstants.TIME_PATTERN)))
                .deserializerByType(LocalDateTime.class,
                        new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(CommonConstants.DATE_TIME_PATTERN)))
                .deserializerByType(LocalDate.class,
                        new LocalDateDeserializer(DateTimeFormatter.ofPattern(CommonConstants.DATE_PATTERN)))
                .deserializerByType(LocalTime.class,
                        new LocalTimeDeserializer(DateTimeFormatter.ofPattern(CommonConstants.TIME_PATTERN)));
    }

    // ==================== 序列化 ====================

    /**
     * 对象转 JSON 字符串。
     *
     * @throws BusinessException 序列化失败时抛出，调用方无需处理受检异常
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + object.getClass().getName());
        }
    }

    /**
     * 对象转格式化 JSON 字符串（便于日志排查，生产环境谨慎使用）。
     */
    public static String toPrettyJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + object.getClass().getName());
        }
    }

    // ==================== 反序列化 ====================

    /**
     * JSON 字符串转对象。
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new BusinessException("JSON 反序列化失败：" + clazz.getName());
        }
    }

    /**
     * JSON 字符串转泛型对象，用于 {@code Result<T>}、{@code Map<String, Object>} 等嵌套泛型。
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new BusinessException("JSON 反序列化失败：" + typeReference.getType());
        }
    }

    /**
     * JSON 数组字符串转 List。
     */
    public static <T> List<T> parseList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
            return MAPPER.readValue(json, javaType);
        } catch (Exception e) {
            throw new BusinessException("JSON 反序列化失败：List<" + elementType.getName() + ">");
        }
    }

    /**
     * 解析为树模型，适合只读取个别字段的场景。
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BusinessException("JSON 解析失败：非法 JSON 结构");
        }
    }

    /**
     * 对象间转换，等价于「先序列化再反序列化」，常用于深拷贝与 Map → DTO。
     */
    public static <T> T convert(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(source, targetType);
        } catch (Exception e) {
            throw new BusinessException("对象转换失败：" + targetType.getName());
        }
    }
}
