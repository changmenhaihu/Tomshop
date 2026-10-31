package com.tom.tomshop.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.tom.tomshop.config.properties.TomShopProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 两级缓存：Caffeine（L1，进程内）→ Redis（L2，跨实例）→ 数据库。
 *
 * <h2>读路径</h2>
 * <pre>
 *   L1 命中 → 直接返回
 *   L1 未命中 → 查 L2 → 命中则回填 L1
 *   L2 未命中 → 加分布式锁 → 二次检查 L2 → 回源数据库 → 写 L2 + L1
 * </pre>
 *
 * <h2>为什么是「多级」而不是只用 Redis</h2>
 * <ul>
 *   <li>L1 把最热的数据留在 JVM 堆里，单次读取是纳秒级，连网络往返都省了；</li>
 *   <li>L2 保证多实例看到同一份数据，且服务重启后缓存不丢；</li>
 *   <li>只读高频、写很少的数据（分类树、商品详情）从这套结构里收益最大。</li>
 * </ul>
 *
 * <h2>三个经典问题的处理</h2>
 * <table border="1">
 *   <tr><th>问题</th><th>做法</th></tr>
 *   <tr>
 *     <td>穿透</td>
 *     <td>loader 返回 null 时写入「空值标记」（{@link #NULL_MARKER}），短 TTL。
 *         不存在的 ID 不会每次都落到数据库。</td>
 *   </tr>
 *   <tr>
 *     <td>击穿</td>
 *     <td>回源前抢 Redisson 锁；抢到的线程查库并回填，其余线程等锁后二次检查 L2。
 *         等锁超时则<b>降级为直接查库且不写缓存</b> —— 宁可少一次缓存收益，
 *         也不让线程全部堵在锁上把 Tomcat 线程池耗尽。</td>
 *   </tr>
 *   <tr>
 *     <td>雪崩</td>
 *     <td>Redis TTL 叠加 ±{@code ttl-jitter-ratio} 的随机抖动，避免同一批键同时过期。</td>
 *   </tr>
 * </table>
 *
 * <h2>一致性</h2>
 * <p>写操作走「更新数据库 → <b>事务提交后</b>删除缓存」，原因有两层：
 * <ol>
 *   <li>若在事务提交前删除，并发读请求会在「缓存已删、数据未提交」的窗口里读到旧值并重新写回缓存，
 *       旧值反而被固化下来；</li>
 *   <li>只删不更新（而非「先更新缓存」），因为并发写时更新缓存的顺序可能与数据库提交顺序相反。
 *       删除是幂等的，晚到的删除最多多一次回源。</li>
 * </ol>
 * 删除后还会通过 Redis pub/sub 广播，让其他实例同步清掉各自的 L1 ——
 * 否则 A 机更新数据后，B 机的 L1 仍会继续返回旧值，直到本地 TTL 到期。
 *
 * <h2>失败策略：缓存永远不能拖垮业务</h2>
 * <p>L2 读写、广播、加锁的任何异常都只记日志并降级（读→回源，写→跳过），绝不上抛。
 * Redis 故障期间按 {@code remote-retry-backoff-millis} 熔断，避免每个请求都去撞一个
 * 已确认不可用的连接并刷满日志。
 *
 * @author Tom
 */
@Slf4j
@Component
public class MultiLevelCache {

    /** Redis 中的空值标记。刻意选一个不可能与正常 JSON 冲突的字符串 */
    static final String NULL_MARKER = "__TOMSHOP_CACHE_NULL__";

    /** L1 中表示「数据库里也没有」的哨兵对象；Caffeine 不允许存 null 值 */
    private static final Object NULL_HOLDER = new Object();

    private final TomShopProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonLockManager lockManager;
    private final CacheJsonCodec codec;
    private final ScheduledExecutorService evictionScheduler;
    private final Cache<String, LocalEntry> localCache;

    private final AtomicLong localHits = new AtomicLong();
    private final AtomicLong remoteHits = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();
    private final AtomicLong nullHits = new AtomicLong();
    private final AtomicLong degradedLoads = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong remoteFailures = new AtomicLong();

    /** L2 熔断截止时间戳（毫秒）。在此之前跳过 Redis，直接当作未命中 */
    private volatile long remoteDisabledUntilMillis;

    public MultiLevelCache(TomShopProperties properties,
                           StringRedisTemplate stringRedisTemplate,
                           RedissonLockManager lockManager,
                           CacheJsonCodec codec,
                           ScheduledExecutorService cacheEvictionScheduler) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockManager = lockManager;
        this.codec = codec;
        this.evictionScheduler = cacheEvictionScheduler;
        this.localCache = buildLocalCache(properties.getCache().getLocalMaximumSize());
    }

    // ==================== 读 ====================

    /**
     * 读取缓存，未命中时调用 {@code loader} 回源并按策略回填。
     *
     * @param key    业务键（见 {@link CacheKeys}），不含 Redis 前缀
     * @param type   目标类型。用 {@code TypeReference} 而非 {@code Class}，
     *               以便支持 {@code List<CategoryVO>} 这类泛型值
     * @param loader 回源函数；返回 {@code null} 表示「数据确实不存在」，会被空值缓存
     * @param policy 本键的 TTL 策略
     * @return 缓存值或回源结果；可能为 {@code null}
     */
    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader, CachePolicy policy) {
        if (!properties.getCache().isEnabled()) {
            return loader.get();
        }
        CachePolicy effective = policy == null ? defaultPolicy() : policy;

        LocalEntry local = localCache.getIfPresent(key);
        if (local != null) {
            localHits.incrementAndGet();
            return unwrap(local);
        }

        RemoteLookup<T> remote = lookupRemote(key, type, effective.localTtlSeconds());
        if (remote.state() == RemoteState.HIT) {
            remoteHits.incrementAndGet();
            return remote.value();
        }
        if (remote.state() == RemoteState.NULL_CACHED) {
            nullHits.incrementAndGet();
            return null;
        }

        // ---- 回源（带击穿保护） ----
        String lockKey = redisKey("lock:" + key);
        boolean locked = lockManager.tryLock(lockKey,
                properties.getCache().getLockWaitSeconds(),
                properties.getCache().getLockLeaseSeconds());
        if (!locked) {
            // 降级：不缓存、不死等。热点键在极端并发下的最坏代价是多次查库，
            // 而不是请求线程全部堆在锁上
            degradedLoads.incrementAndGet();
            log.debug("[缓存] 未取得回源锁，降级直查：{}", key);
            return loader.get();
        }
        try {
            // 二次检查：等锁期间别的线程可能已经回填
            RemoteLookup<T> again = lookupRemote(key, type, effective.localTtlSeconds());
            if (again.state() == RemoteState.HIT) {
                remoteHits.incrementAndGet();
                return again.value();
            }
            if (again.state() == RemoteState.NULL_CACHED) {
                nullHits.incrementAndGet();
                return null;
            }

            loads.incrementAndGet();
            T value = loader.get();
            writeThrough(key, value, effective);
            return value;
        } finally {
            lockManager.unlock(lockKey);
        }
    }

    // ==================== 写 / 失效 ====================

    /**
     * 立即失效（清 L1 + 删 L2 + 广播）。适用于「不涉及数据库写」的重置场景。
     *
     * <p>业务写入一律用 {@link #evictAfterCommit(String...)}，不要直接调本方法。
     */
    public void evict(String key) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        evictions.incrementAndGet();
        evictLocal(key);
        deleteRemote(key);
        publishInvalidation(key);
    }

    /**
     * 在当前事务<b>提交之后</b>失效一组键。
     *
     * <p>这是业务写的唯一入口。事务未提交就删缓存，会让并发读把旧值重新写回；
     * 事务回滚时也不应该动缓存。没有事务上下文时按立即失效处理。
     *
     * <p>失效动作本身吞掉所有异常 —— 缓存清理失败最多让数据陈旧一个 TTL，
     * 不能反过来让一次成功的数据库写入返回 500。
     */
    public void evictAfterCommit(String... keys) {
        if (!properties.getCache().isEnabled() || keys.length == 0) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvictAndSchedule(keys);
                }
            });
        } else {
            doEvictAndSchedule(keys);
        }
    }

    /**
     * 只清本实例的 L1。供失效广播监听器调用。
     */
    public void evictLocal(String key) {
        localCache.invalidate(key);
    }

    /**
     * 运行期统计。测试与排障用；生产可接到 Micrometer。
     */
    public CacheStats stats() {
        return new CacheStats(
                localHits.get(),
                remoteHits.get(),
                loads.get(),
                nullHits.get(),
                degradedLoads.get(),
                evictions.get(),
                remoteFailures.get(),
                localCache.stats().hitCount(),
                localCache.stats().missCount(),
                localCache.estimatedSize());
    }

    /** 清空 L1，仅用于测试或运维手动干预 */
    public void clearLocal() {
        localCache.invalidateAll();
    }

    // ==================== 内部实现 ====================

    private void doEvictAndSchedule(String[] keys) {
        for (String key : keys) {
            try {
                evict(key);
            } catch (Exception e) {
                log.warn("[缓存] 失效失败，忽略：key={}, cause={}", key, e.getMessage());
            }
            scheduleSecondEviction(key);
        }
    }

    /**
     * 延迟二次删除。
     *
     * <p>解决「读-删-回填」竞态：某读请求在写事务提交<b>前</b>未命中，查到了旧值，
     * 却在本实例删缓存<b>后</b>才把旧值写进 L2。500ms 后的第二次删除覆盖这个窗口。
     * 这是有界的补偿手段，不是分布式事务 —— 剩余窗口由短 TTL 兜底。
     */
    private void scheduleSecondEviction(String key) {
        long delay = properties.getCache().getDelayedEvictMillis();
        if (delay <= 0) {
            return;
        }
        evictionScheduler.schedule(() -> {
            try {
                evictLocal(key);
                deleteRemote(key);
            } catch (Exception e) {
                log.debug("[缓存] 延迟二次失效失败：key={}, cause={}", key, e.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 读 L2，一次网络往返完成「判空 / 判断空值标记 / 解码」。
     *
     * <p>命中真实值时会顺手回填 L1 —— L2 命中的下一跳几乎必然还是同一个热点键。
     */
    private <T> RemoteLookup<T> lookupRemote(String key, TypeReference<T> type, long localTtlSeconds) {
        String json = readRemote(key);
        if (json == null) {
            return new RemoteLookup<>(RemoteState.MISS, null);
        }
        if (NULL_MARKER.equals(json)) {
            putLocal(key, NULL_HOLDER, properties.getCache().getNullTtlSeconds());
            return new RemoteLookup<>(RemoteState.NULL_CACHED, null);
        }
        T value = codec.decode(json, type);
        if (value == null) {
            // 脏缓存（例如类结构变更前写入的旧格式）：删掉，按未命中回源
            deleteRemote(key);
            return new RemoteLookup<>(RemoteState.MISS, null);
        }
        putLocal(key, value, localTtlSeconds);
        return new RemoteLookup<>(RemoteState.HIT, value);
    }

    private void writeThrough(String key, Object value, CachePolicy policy) {
        if (value == null) {
            long nullTtl = properties.getCache().getNullTtlSeconds();
            writeRemote(key, NULL_MARKER, nullTtl);
            putLocal(key, NULL_HOLDER, nullTtl);
            return;
        }
        writeRemote(key, codec.encode(value), jittered(policy.remoteTtlSeconds()));
        putLocal(key, value, policy.localTtlSeconds());
    }

    private void putLocal(String key, Object value, long ttlSeconds) {
        localCache.put(key, new LocalEntry(value, TimeUnit.SECONDS.toNanos(ttlSeconds)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T unwrap(LocalEntry entry) {
        return entry.value() == NULL_HOLDER ? null : (T) entry.value();
    }

    private String readRemote(String key) {
        if (isRemoteDisabled()) {
            return null;
        }
        try {
            return stringRedisTemplate.opsForValue().get(redisKey(key));
        } catch (Exception e) {
            markRemoteFailure("读取", e);
            return null;
        }
    }

    private void writeRemote(String key, String value, long ttlSeconds) {
        if (isRemoteDisabled()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(redisKey(key), value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            markRemoteFailure("写入", e);
        }
    }

    private void deleteRemote(String key) {
        if (isRemoteDisabled()) {
            return;
        }
        try {
            stringRedisTemplate.delete(redisKey(key));
        } catch (Exception e) {
            markRemoteFailure("删除", e);
        }
    }

    private void publishInvalidation(String key) {
        if (isRemoteDisabled()) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(properties.getCache().getInvalidationChannel(), key);
        } catch (Exception e) {
            markRemoteFailure("广播", e);
        }
    }

    private boolean isRemoteDisabled() {
        return System.currentTimeMillis() < remoteDisabledUntilMillis;
    }

    private void markRemoteFailure(String operation, Exception e) {
        long backoff = properties.getCache().getRemoteRetryBackoffMillis();
        long now = System.currentTimeMillis();
        // 只在熔断窗口刚开启时告警一次，避免 Redis 长时间不可用把日志刷爆
        if (now >= remoteDisabledUntilMillis) {
            log.warn("[缓存] Redis {} 失败，{}ms 内跳过 L2：{}", operation, backoff, e.getMessage());
        }
        remoteDisabledUntilMillis = now + backoff;
        remoteFailures.incrementAndGet();
    }

    private long jittered(long baseTtlSeconds) {
        double ratio = properties.getCache().getTtlJitterRatio();
        long delta = (long) (baseTtlSeconds * ratio);
        if (ratio <= 0 || delta <= 0) {
            return baseTtlSeconds;
        }
        // 只往正方向抖也可以，但双向抖动分布更均匀；夹到 1 秒以上防止异常小 TTL
        return Math.max(1L, baseTtlSeconds + ThreadLocalRandom.current().nextLong(-delta, delta + 1));
    }

    private String redisKey(String key) {
        return properties.getCache().getKeyPrefix() + key;
    }

    private CachePolicy defaultPolicy() {
        TomShopProperties.Cache cache = properties.getCache();
        return CachePolicy.of(cache.getLocalTtlSeconds(), cache.getRemoteTtlSeconds());
    }

    private static Cache<String, LocalEntry> buildLocalCache(long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .recordStats()
                // 每个条目自带 TTL：分类树能放几分钟，商品详情只能放几十秒，
                // 用统一的 expireAfterWrite 无法同时表达这两种策略
                .expireAfter(new Expiry<String, LocalEntry>() {
                    @Override
                    public long expireAfterCreate(String key, LocalEntry value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, LocalEntry value,
                                                  long currentTime, long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, LocalEntry value,
                                                long currentTime, long currentDuration) {
                        // 读操作不续期：L1 的寿命应由写入时间决定，而不是访问频率
                        return currentDuration;
                    }
                })
                .build();
    }

    /** L1 条目：值与存活时长（纳秒） */
    private record LocalEntry(Object value, long ttlNanos) {
    }

    /** L2 查询结果：区分「未命中」与「命中了空值标记」——两者都不能返回给调用方，但统计口径不同 */
    private enum RemoteState { HIT, NULL_CACHED, MISS }

    private record RemoteLookup<T>(RemoteState state, T value) {
    }

    /**
     * 缓存运行指标快照。
     *
     * @param localHits      L1 命中次数
     * @param remoteHits     L2 命中次数（含等锁后的二次命中）
     * @param loads          回源数据库次数
     * @param nullHits       命中空值缓存次数（这些请求本会打到数据库）
     * @param degradedLoads  抢锁失败、降级直查次数
     * @param evictions      主动失效次数
     * @param remoteFailures L2 操作失败次数（含熔断期间的跳过）
     * @param caffeineHits   Caffeine 自身的命中计数（与 localHits 交叉校验用）
     * @param caffeineMisses Caffeine 自身的未命中计数
     * @param localSize      L1 估算条目数
     */
    public record CacheStats(long localHits, long remoteHits, long loads, long nullHits,
                             long degradedLoads, long evictions, long remoteFailures,
                             long caffeineHits, long caffeineMisses, long localSize) {

        @Override
        public String toString() {
            return "CacheStats{l1=" + localHits + ", l2=" + remoteHits + ", db=" + loads
                    + ", nullHit=" + nullHits + ", degraded=" + degradedLoads
                    + ", evict=" + evictions + ", redisFail=" + remoteFailures
                    + ", size=" + localSize + '}';
        }

        /** 便于测试输出全部字段 */
        public String summary() {
            return Arrays.toString(new long[]{localHits, remoteHits, loads, nullHits,
                    degradedLoads, evictions, remoteFailures});
        }
    }
}
