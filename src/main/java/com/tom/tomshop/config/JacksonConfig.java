package com.tom.tomshop.config;

import com.tom.tomshop.utils.JsonUtils;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 层 Jackson 配置。
 *
 * <p>本类刻意只做一件事：把 {@link JsonUtils#applyTo} 注册为 Spring Boot 的
 * {@code Jackson2ObjectMapperBuilderCustomizer}。所有序列化规则（Long → String 的雪花 ID 精度保护、
 * 时间格式、未知字段容忍）都定义在 {@code JsonUtils} 一处，
 * 从而让「HTTP 响应」与「Redis / MQ 中的 JSON」行为完全一致。
 *
 * <p>关于 {@code application.yml} 中的 {@code spring.jackson.*}：Boot 内置的 customizer
 * order 为 0，本 bean 未标注 {@code @Order}（LOWEST_PRECEDENCE）故会后执行、优先级更高，
 * 冲突时以本类规则为准。
 *
 * @author Tom
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer tomShopJacksonCustomizer() {
        return JsonUtils::applyTo;
    }
}
