package com.tom.tomshop.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权装配。
 *
 * <p><b>一、令牌形态</b>：注册 {@link StpLogicJwtForSimple} 后，
 * Sa-Token 把登录令牌签发成 JWT（密钥取 {@code sa-token.jwt-secret-key}），
 * 前端用 {@code Authorization: Bearer <jwt>} 携带。之所以选 Simple 而不是 Stateless：
 * <ul>
 *   <li>Stateless 模式完全不落库，<b>无法主动注销、无法封禁、无法强制下线</b>，
 *       账号被盗后只能等令牌自然过期，电商场景不能接受；</li>
 *   <li>Simple 模式把会话写进 Redis（{@code sa-token-redis-jackson}），
 *       既保留了 JWT 的自包含与无状态校验优势，又保留了服务端吊销能力。</li>
 * </ul>
 *
 * <p><b>二、放行规则分两类</b>：
 * <ul>
 *   <li>{@link #ANONYMOUS_PATHS}：登录、注册、刷新令牌、探活与接口文档，任何请求方法都放行；</li>
 *   <li>{@link #ANONYMOUS_GET_PATHS}：商品与分类的 <b>GET</b> 接口。B2C 前台未登录也要能逛商品，
 *       但这些路径下的 POST/PUT/DELETE 属于后台维护，必须登录且另有权限点把关。</li>
 * </ul>
 * 其余接口统一要求登录。权限粒度（如「谁可以发货」）不在这里用路径硬编码，
 * 而是由 Controller 上的 {@code @SaCheckPermission} 声明，避免鉴权规则分散到两处。
 *
 * @author Tom
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 完全匿名放行的路径（含所有请求方法） */
    private static final String[] ANONYMOUS_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/health",
            "/actuator/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/favicon.ico",
            "/error"
    };

    /** 允许匿名「只读」的路径：仅 GET 放行，写操作仍需登录 */
    private static final String[] ANONYMOUS_GET_PATHS = {
            "/api/v1/products/**",
            "/api/v1/categories/**"
    };

    /** 拦截器顺序：鉴权必须先于身份上下文解析，避免把非法令牌写进 ThreadLocal */
    private static final int SA_INTERCEPTOR_ORDER = 0;
    public static final int USER_CONTEXT_INTERCEPTOR_ORDER = 100;

    /**
     * 覆盖默认的 {@code StpLogic}，使令牌以 JWT 形式签发。
     *
     * <p>Sa-Token 启动时会扫描容器内的 {@link StpLogic} Bean 并替换全局默认实现，
     * 因此不需要额外调用 {@code SaManager.setStpLogic}。
     */
    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/**")
                        .notMatch(ANONYMOUS_PATHS)
                        .check(r -> {
                            if (isAnonymousRead()) {
                                return;
                            }
                            // 未登录抛 NotLoginException，由 GlobalExceptionHandler 统一转 401
                            StpUtil.checkLogin();
                        })))
                .addPathPatterns("/**")
                .order(SA_INTERCEPTOR_ORDER);
    }

    /**
     * 判断当前请求是否属于「匿名可读」：必须是 GET，且路径落在 {@link #ANONYMOUS_GET_PATHS} 内。
     *
     * <p>SaRouter 没有提供「方法 + 路径」组合的 notMatch 重载，所以这里显式写出来。
     * 用 SaRouter 自带的匹配器而不用 {@code startsWith}，是为了与上面的 Ant 风格通配符保持一致。
     */
    private static boolean isAnonymousRead() {
        if (!SaRouter.isMatchCurrMethod(new SaHttpMethod[]{SaHttpMethod.GET})) {
            return false;
        }
        return SaRouter.isMatchCurrURI(ANONYMOUS_GET_PATHS);
    }
}
