package com.tom.tomshop.config;

import com.tom.tomshop.config.properties.TomShopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（前后端分离必需）。
 *
 * <p>阶段 3 接入 Sa-Token 后的注意事项：Sa-Token 的拦截器会在鉴权前先判断登录态，
 * 而预检请求 {@code OPTIONS} 不携带 {@code Authorization} 头，必须在 Sa-Token 的
 * 拦截规则里显式放行，否则跨域会以「预检 401」的形式失败 —— 表现为「接口用 Postman
 * 能调通、浏览器里全部报 CORS 错误」，排查时容易误判成跨域配置写错。
 *
 * @author Tom
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final TomShopProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        TomShopProperties.Cors cors = properties.getCors();

        registry.addMapping("/**")
                .allowedOriginPatterns(cors.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(cors.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAge());
    }
}
