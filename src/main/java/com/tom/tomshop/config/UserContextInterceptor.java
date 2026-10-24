package com.tom.tomshop.config;

import cn.dev33.satoken.stp.StpUtil;
import com.tom.tomshop.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户身份解析拦截器：把 Sa-Token 的登录态写入 {@link UserContext}。
 *
 * <p><b>阶段 3 起</b>身份来源是 {@code StpUtil.getLoginIdAsLong()}，即请求头
 * {@code Authorization: Bearer <jwt>} 解析出的登录态。阶段 2 临时的 {@code X-User-Id}
 * 请求头已被移除 —— 那段逻辑没有任何安全性，保留下来只会成为后门。
 *
 * <p><b>这里不做「是否必须登录」的判断</b>：那件事由 {@code SaTokenConfig} 里的
 * {@link cn.dev33.satoken.interceptor.SaInterceptor} 负责。本拦截器的职责只有一个 ——
 * 把已经通过鉴权的用户 ID 搬到 ThreadLocal，让 Service 层不必层层传参。
 * 两者分开放，是因为「谁能访问这个 URL」和「当前请求代表谁」是两个问题，
 * 混在一个类里会让放行白名单和身份解析互相牵制。
 *
 * <p>{@link #afterCompletion} 的清理不能省：Tomcat 复用线程，不清理会让下一个请求
 * 读到上一个用户的身份 —— A 的购物车被 B 看到，是真实发生过的安全事故。
 *
 * @author Tom
 */
@Slf4j
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // isLogin() 内部已完成令牌解析与签名校验，未登录或令牌失效时返回 false，不抛异常，
        // 保证匿名接口（商品浏览）照常可用
        if (StpUtil.isLogin()) {
            UserContext.setUserId(StpUtil.getLoginIdAsLong());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
