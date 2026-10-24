package com.tom.tomshop.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 拦截器注册。
 *
 * <p>与 {@link CorsConfig} 分开：跨域是「响应头改写」，拦截器是「请求前后处理」，
 * 两者关注点不同，拆成各自的配置类便于独立演进。
 *
 * <p><b>鉴权拦截器不在这里注册</b>，而在 {@link SaTokenConfig}：Sa-Token 的拦截器是
 * {@code WebMvcConfigurer} 的一部分又同时掌握放行白名单，放在一起改动时不用跳文件。
 * 本项目两个 {@code WebMvcConfigurer} 实现各自注册自己的拦截器，通过 {@code order}
 * 串成确定的顺序：
 * <pre>
 *   SaInterceptor(order=0)        -> 判定是否登录、校验 @SaCheckPermission
 *   UserContextInterceptor(100)   -> 把已通过鉴权的用户写入 ThreadLocal
 * </pre>
 * 顺序不能反：先解析身份再鉴权，会把「令牌非法」请求的用户 ID 短暂写进 ThreadLocal，
 * 一旦某条分支提前返回，这个脏值就会跟着线程漂到下一个请求。
 *
 * @author Tom
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                // 拦截全部业务接口：公开接口也要保证 ThreadLocal 被正确清理
                .addPathPatterns("/**")
                // 文档与探活端不涉及用户身份，排除掉减少无谓开销
                .excludePathPatterns(
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/actuator/**")
                .order(SaTokenConfig.USER_CONTEXT_INTERCEPTOR_ORDER);
    }
}
