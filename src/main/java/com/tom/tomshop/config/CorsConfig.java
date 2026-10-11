package com.tom.tomshop.config;

import com.tom.tomshop.config.properties.TomShopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（前后端分离必需）。
 *
 * <p>阶段 3 接入 Shiro 后的注意事项：Shiro 的过滤器链会先于 DispatcherServlet 执行，
 * 预检请求 {@code OPTIONS} 不带 {@code Authorization} 头，必须在 Shiro FilterChain 中放行，
 * 否则跨域会以「预检 401」的形式失败。届时会在 ShiroConfig 中显式放行 OPTIONS。
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
