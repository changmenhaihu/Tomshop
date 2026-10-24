package com.tom.tomshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器。
 *
 * <p>只引入 {@code spring-security-crypto} 这一个纯工具包，<b>不引入</b>
 * {@code spring-boot-starter-security}：后者会自动装配一整条 Servlet 过滤器链并默认拦截所有请求，
 * 与 Sa-Token 的拦截器方案职责重叠，调起来非常痛苦。
 *
 * <p><b>为什么是 BCrypt 而不是 MD5/SHA</b>：BCrypt 自带随机盐并可通过 cost 参数调节计算成本，
 * 同样的明文每次加密结果都不同，彩虹表与批量撞库全部失效。MD5 类算法在电商用户密码场景已不可用。
 * 默认强度 10 约 100ms/次，登录接口完全可接受。
 *
 * @author Tom
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
