package com.tom.tomshop.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.tom.tomshop.common.constant.AuthConstants;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.config.properties.TomShopProperties;
import com.tom.tomshop.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 刷新令牌管理（双令牌方案的「长令牌」一侧）。
 *
 * <p><b>为什么需要它</b>：access token 是 JWT，一旦签发在过期前无法收回；为了让被盗令牌的
 * 危害窗口尽量小，access token 只给 2 小时。但如果只有它，用户每两小时就要重新输一次密码。
 * 于是补一个有效期更长（默认 7 天）的 refresh token，专门用来「换一个新的 access token」。
 *
 * <p><b>与 access token 的三点差异</b>：
 * <ol>
 *   <li>用途不同：refresh token 只能调 {@code POST /auth/refresh}，其余接口一律拒收。
 *       实现上靠 JWT 里的 {@code typ=refresh} 声明区分，access token 没有这个声明。</li>
 *   <li>一次性：每次刷新后旧的立即作废（<b>轮换</b>），Redis 里 {@code jti} 的 DELETE
 *       返回 0 说明这个令牌已经用过，直接拒绝 —— 这是发现令牌泄露的主要手段：
 *       攻击者用过的令牌，合法用户再用就会失败。</li>
 *   <li>可吊销：登录态在 Redis 里，登出时按用户索引批量删除，不必等 JWT 自然过期。</li>
 * </ol>
 *
 * <p><b>为什么不用 Sa-Token 自带的 token 续期</b>：Sa-Token 的 {@code active-timeout}
 * 是「滑动过期」，只要用户持续活跃，同一个 token 就永远有效，等于取消了短时效。
 * 双令牌把「短期凭证」和「长期凭证」的职责分开，两者互不替代。
 *
 * <p><b>Redis 键设计</b>：
 * <pre>
 *   tomshop:auth:refresh:{jti}          -> userId     单令牌，用于一次性消费
 *   tomshop:auth:refresh:uid:{userId}   -> Set&lt;jti&gt;   用户索引，用于登出时批量吊销
 * </pre>
 * 只存 {@code jti} 不存整串 JWT：令牌本身由签名保证不可篡改，库里存它没有意义，
 * 反而会把敏感串散落到备份与慢查询日志里。
 *
 * @author Tom
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final TomShopProperties properties;

    /**
     * 签发一个新的 refresh token，并在 Redis 登记。
     *
     * @param userId 用户 ID，必须已通过密码校验
     * @return JWT 字符串，交给前端保存
     */
    public String issue(Long userId) {
        long timeout = properties.getAuth().getRefreshTokenTimeout();
        String jti = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> extra = new LinkedHashMap<>(4);
        extra.put(AuthConstants.CLAIM_JTI, jti);
        extra.put(AuthConstants.CLAIM_TYPE, AuthConstants.TOKEN_TYPE_REFRESH);

        // 与 access token 使用同一把密钥：两者本属同一信任域，共用密钥不会扩大攻击面，
        // 而 typ 声明保证 refresh token 无法冒充 access token（反之亦然）
        String token = SaJwtUtil.createToken(
                StpUtil.getLoginType(), userId, "default", timeout, extra, jwtSecretKey());

        String jtiKey = keyOfJti(jti);
        String userKey = keyOfUser(userId);
        stringRedisTemplate.opsForValue().set(jtiKey, String.valueOf(userId), Duration.ofSeconds(timeout));
        stringRedisTemplate.opsForSet().add(userKey, jti);
        // 索引键存活时间与令牌一致：即便用户再不登录，索引也会在同一时刻自然消失，不会无限增长
        stringRedisTemplate.expire(userKey, Duration.ofSeconds(timeout));
        return token;
    }

    /**
     * 校验并消费一个 refresh token（轮换语义：调用成功后该令牌立即失效）。
     *
     * @param refreshToken 前端提交的 refresh token
     * @return 令牌所属用户 ID
     * @throws BusinessException 令牌过期、被篡改、类型不符或已被使用过时抛出 401
     */
    public Long consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌不能为空");
        }
        String keyt = jwtSecretKey();
        String loginType = StpUtil.getLoginType();

        JSONObject payloads;
        Object loginId;
        try {
            // getPayloads 内部校验签名与 exp，过期或篡改都会抛异常
            payloads = SaJwtUtil.getPayloads(refreshToken, loginType, keyt);
            loginId = SaJwtUtil.getLoginId(refreshToken, loginType, keyt);
        } catch (Exception e) {
            log.warn("[刷新令牌] 校验失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已过期，请重新登录");
        }
        if (payloads == null
                || !AuthConstants.TOKEN_TYPE_REFRESH.equals(payloads.getStr(AuthConstants.CLAIM_TYPE))) {
            // 典型场景：把 access token 拿到刷新接口来用，或者伪造了 typ
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效，请重新登录");
        }

        String jti = payloads.getStr(AuthConstants.CLAIM_JTI);
        if (!StringUtils.hasText(jti)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效，请重新登录");
        }
        Long userId = parseUserId(loginId);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌无效，请重新登录");
        }

        // 一次性消费：DELETE 返回 0 说明已被用过（可能是并发刷新，也可能是令牌泄露后被抢先使用）
        Boolean removed = stringRedisTemplate.delete(keyOfJti(jti));
        if (!Boolean.TRUE.equals(removed)) {
            log.warn("[刷新令牌] 检测到重复使用，已拒绝。userId={}, jti={}", userId, jti);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        stringRedisTemplate.opsForSet().remove(keyOfUser(userId), jti);
        return userId;
    }

    /**
     * 吊销某用户的全部 refresh token（登出时调用）。
     *
     * <p>即使索引读失败也不影响登出成功：access token 已由 {@code StpUtil.logout()} 销毁，
     * 遗留的 refresh token 会因 Redis 中 jti 过期（最长 7 天）而自然失效。
     */
    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String userKey = keyOfUser(userId);
            Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey);
            if (jtis != null && !jtis.isEmpty()) {
                Collection<String> keys = jtis.stream().map(this::keyOfJti).toList();
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(userKey);
        } catch (Exception e) {
            log.warn("[刷新令牌] 批量吊销失败，忽略。userId={}, cause={}", userId, e.getMessage());
        }
    }

    private String keyOfJti(String jti) {
        return properties.getAuth().getRefreshTokenPrefix() + jti;
    }

    private String keyOfUser(Long userId) {
        return properties.getAuth().getRefreshTokenPrefix() + "uid:" + userId;
    }

    private static String jwtSecretKey() {
        return SaManager.getConfig().getJwtSecretKey();
    }

    private static Long parseUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return loginId == null ? null : Long.valueOf(loginId.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
