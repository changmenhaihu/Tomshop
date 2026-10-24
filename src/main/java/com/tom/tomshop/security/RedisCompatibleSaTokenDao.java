package com.tom.tomshop.security;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 兼容 Redis 5 的 Sa-Token 会话存储实现。
 *
 * <p><b>为什么需要这个类</b>：{@code sa-token-redis-jackson} 自带的
 * {@link SaTokenDaoForRedisTemplate#setStringAndKeepTTL(String, String)} 使用
 * {@code SET key value KEEPTTL} 续写会话值并保留原有 TTL，而 {@code KEEPTTL} 是
 * <b>Redis 6.0</b> 才引入的语法。本机开发环境是 Redis 5.0.14（Windows 版长期停留在 5.x），
 * 首次登录就会抛 {@code ERR syntax error}，表现是「密码正确却返回 5000 系统异常」，
 * 且堆栈埋在 Sa-Token 内部，很难一眼看出是 Redis 版本问题。
 *
 * <p><b>替代实现</b>：先 {@code TTL} 再 {@code SET EX}，语义等价（读改写不是原子操作，
 * 但会话值更新本来就是「后写覆盖前写」，且 TTL 只会被略微提前，不会造成安全问题）。
 * 若原键没有 TTL（{@code -1}）或已不存在（{@code -2}），退化为普通 {@code SET}。
 *
 * <p><b>升级路径</b>：Redis 升到 6.0+ 后，本类可直接删除，
 * 让 {@code SaTokenDaoForRedisTemplate} 生效即可 —— 它的其他方法都与本类完全一致。
 *
 * @author Tom
 */
@Slf4j
public class RedisCompatibleSaTokenDao extends SaTokenDaoForRedisTemplate {

    @Override
    public void setStringAndKeepTTL(String key, String value) {
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire != null && expire > 0) {
            stringRedisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
        } else {
            // 无 TTL 或键不存在：直接覆盖，不引入 TTL。会话本身由 Sa-Token 控制生命周期
            stringRedisTemplate.opsForValue().set(key, value);
        }
    }
}
