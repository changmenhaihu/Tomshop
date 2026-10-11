package com.tom.tomshop.config;

import com.tom.tomshop.config.properties.TomShopProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 *
 * <p>文档地址：{@code http://localhost:8080/doc.html}
 *
 * <p>这里只注册了 SecurityScheme 定义，<b>没有</b>声明的全局安全要求。
 * 原因是阶段 0 的接口（健康检查、后续的商品/分类浏览）是免登录的，
 * 若加全局 {@code addSecurityItem}，Swagger UI 会给所有接口挂锁并强制填 Token，
 * 造成误导。阶段 3 接入双 JWT 后，改为在需要鉴权的 Controller 上标注
 * {@code @SecurityRequirement(name = Knife4jConfig.SECURITY_SCHEME_NAME)}，精确到接口。
 *
 * @author Tom
 */
@Configuration
@RequiredArgsConstructor
public class Knife4jConfig {

    /** 安全方案名称，阶段 3 的 Controller 鉴权注解需引用同一常量 */
    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    private final TomShopProperties properties;

    @Bean
    public OpenAPI tomShopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TomShop 电商平台 API")
                        .description("""
                                TomShop 前后端分离 B2C 电商单体后端接口文档。

                                **统一响应结构**：所有接口返回 `Result<T>`，`code = 200` 表示业务成功。
                                `code` 编码规范见 `ResultCode` 枚举。

                                **ID 精度约定**：所有 ID 字段在 JSON 中为**字符串**（Java 雪花 ID 为 19 位，
                                超出 JS 安全整数范围），请勿在前端做数值运算后回传。""")
                        .version(properties.getApplicationVersion())
                        .contact(new Contact().name("Tom").email("tom@example.com"))
                        .license(new License().name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("阶段 3 接入双 JWT 后，在此填入登录接口返回的 Access Token")));
    }
}
