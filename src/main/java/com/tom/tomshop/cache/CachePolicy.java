package com.tom.tomshop.cache;

/**
 * 单个缓存键的 TTL 策略。
 *
 * <p>为什么 TTL 不写死在缓存组件里：不同数据的「可容忍陈旧时长」差得很远 ——
 * 分类树几十分钟不变，商品详情里的库存却随时会动。把 TTL 放在调用点，
 * 让每个业务自己声明它能接受多旧的数据，比在缓存层拍一个全局值更准确。
 *
 * @param localTtlSeconds  L1（Caffeine，进程内）存活秒数。通常远小于 L2：
 *                         L1 无法被其他实例的 pub/sub 立刻清掉时，短 TTL 是最后一道兜底
 * @param remoteTtlSeconds L2（Redis）存活秒数，写入时会叠加随机抖动防雪崩
 * @author Tom
 */
public record CachePolicy(long localTtlSeconds, long remoteTtlSeconds) {

    public CachePolicy {
        if (localTtlSeconds <= 0 || remoteTtlSeconds <= 0) {
            throw new IllegalArgumentException("缓存 TTL 必须为正数");
        }
    }

    public static CachePolicy of(long localTtlSeconds, long remoteTtlSeconds) {
        return new CachePolicy(localTtlSeconds, remoteTtlSeconds);
    }
}
