package com.tom.tomshop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tom.tomshop.cache.CacheInvalidationListener;
import com.tom.tomshop.cache.CachePolicy;
import com.tom.tomshop.cache.MultiLevelCache;
import com.tom.tomshop.cache.MultiLevelCache.CacheStats;
import com.tom.tomshop.cache.RedissonLockManager;
import com.tom.tomshop.config.properties.TomShopProperties;
import com.tom.tomshop.dto.CategorySaveDTO;
import com.tom.tomshop.service.CategoryService;
import com.tom.tomshop.vo.CategoryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多级缓存集成测试。
 *
 * <p><b>为什么本类不能加 {@code @Transactional}</b>：
 * {@code evictAfterCommit} 依赖 Spring 事务同步回调，而测试级事务永远不会提交，
 * afterCommit 根本不触发 —— 加了 {@code @Transactional} 测出来的「通过」是假通过。
 * 因此这里每个用例都真实提交、并在 {@code @AfterEach} 中显式清理 Redis 键；
 * 业务数据只创建一条临时分类，并在 finally 中物理删除，不污染开发库。
 *
 * <p><b>统计口径</b>：{@link CacheStats} 的计数器是累计值且不提供重置，
 * 所有断言都基于「用例前后的差值」，避免用例顺序影响结果。
 *
 * @author Tom
 */
@SpringBootTest
class CacheIntegrationTest {

    /** 与 MultiLevelCache 中的包私有常量保持一致（跨包不可见，故此处重复字面量） */
    private static final String NULL_MARKER = "__TOMSHOP_CACHE_NULL__";

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    private static final CachePolicy TEST_POLICY = CachePolicy.of(60, 120);

    @Autowired
    private MultiLevelCache cache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheInvalidationListener invalidationListener;

    @Autowired
    private RedissonLockManager lockManager;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TomShopProperties properties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本用例产生的缓存键，结束后统一删除 */
    private final List<String> keysToClean = new ArrayList<>();

    /** 用例执行前的延迟二次删除配置，结束后还原 */
    private long originalDelayedEvictMillis;

    @BeforeEach
    void setUp() {
        cache.clearLocal();
        originalDelayedEvictMillis = properties.getCache().getDelayedEvictMillis();
        // 测试内关闭延迟二次删除：它会在用例断言之后异步删键，制造难以复现的偶发失败。
        // 「延迟删除本身」由设计保证（见 MultiLevelCache#scheduleSecondEviction），不在本类验证时序。
        properties.getCache().setDelayedEvictMillis(0);
    }

    @AfterEach
    void tearDown() {
        properties.getCache().setDelayedEvictMillis(originalDelayedEvictMillis);
        keysToClean.forEach(key -> stringRedisTemplate.delete(redisKey(key)));
        keysToClean.clear();
        cache.clearLocal();
    }

    // ==================== 读路径 ====================

    @Test
    @DisplayName("读路径：回源一次 → L1 命中 → 清 L1 后 L2 命中，均不再回源")
    void readPathHitsL1ThenL2() {
        String key = newKey("read");
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<String>> loader = () -> {
            loads.incrementAndGet();
            return List.of("a", "b");
        };

        CacheStats start = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("a", "b");
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().loads() - start.loads()).isEqualTo(1);

        // L1 命中
        CacheStats afterFirst = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("a", "b");
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().localHits() - afterFirst.localHits()).isEqualTo(1);

        // L2 写入的是带抖动的 TTL：120s ± 10%，取整误差再留 20s 余量
        Long ttl = stringRedisTemplate.getExpire(redisKey(key), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(100L, 140L);

        // 模拟「这是另一台实例」：本机 L1 为空，但 Redis 仍在
        cache.clearLocal();
        CacheStats afterL1 = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("a", "b");
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().remoteHits() - afterL1.remoteHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("穿透防护：loader 返回 null 时写空值标记，L1 与 L2 都能拦截后续请求")
    void nullResultIsCachedAtBothLevels() {
        String key = newKey("null");
        AtomicInteger loads = new AtomicInteger();
        Supplier<Map<String, String>> loader = () -> {
            loads.incrementAndGet();
            return null;
        };

        assertThat(cache.getOrLoad(key, STRING_MAP_TYPE, loader, TEST_POLICY)).isNull();
        assertThat(loads).hasValue(1);

        // L1 空标记拦截
        CacheStats afterLoad = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_MAP_TYPE, loader, TEST_POLICY)).isNull();
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().localHits() - afterLoad.localHits()).isEqualTo(1);

        // L2 空标记拦截
        cache.clearLocal();
        CacheStats beforeL2 = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_MAP_TYPE, loader, TEST_POLICY)).isNull();
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().nullHits() - beforeL2.nullHits()).isEqualTo(1);

        assertThat(stringRedisTemplate.opsForValue().get(redisKey(key))).isEqualTo(NULL_MARKER);
        Long ttl = stringRedisTemplate.getExpire(redisKey(key), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull()
                .isBetween(1L, properties.getCache().getNullTtlSeconds());
    }

    // ==================== 写路径 ====================

    @Test
    @DisplayName("事务感知：提交前不删缓存，提交后才删并允许重新回源")
    void evictionHappensOnlyAfterCommit() {
        String key = newKey("commit");
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<String>> loader = () -> {
            loads.incrementAndGet();
            return List.of("v" + loads.get());
        };

        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("v1");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            cache.evictAfterCommit(key);
            // 事务尚未提交：L1 必须仍持有旧值，证明删除没有被提前执行
            assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                    .containsExactly("v1");
            assertThat(loads).hasValue(1);
        });

        // 提交后：L1 与 L2 都已失效，下一次读取回源得到新值
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("v2");
        assertThat(loads).hasValue(2);
        assertThat(stringRedisTemplate.hasKey(redisKey(key))).isTrue();
    }

    @Test
    @DisplayName("事务回滚：缓存不得被清理，避免回滚后读到与库中不一致的空窗")
    void evictionIsSkippedWhenTransactionRollsBack() {
        String key = newKey("rollback");
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<String>> loader = () -> {
            loads.incrementAndGet();
            return List.of("stable");
        };

        cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY);
        assertThat(loads).hasValue(1);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            cache.evictAfterCommit(key);
            status.setRollbackOnly();
        });

        CacheStats afterRollback = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("stable");
        assertThat(loads).hasValue(1);
        assertThat(cache.stats().localHits() - afterRollback.localHits()).isEqualTo(1);
        assertThat(stringRedisTemplate.hasKey(redisKey(key))).isTrue();
    }

    @Test
    @DisplayName("跨实例失效：监听广播只清本机 L1，L2 与数据源不受影响")
    void invalidationBroadcastClearsOnlyLocalCache() {
        String key = newKey("broadcast");
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<String>> loader = () -> {
            loads.incrementAndGet();
            return List.of("shared");
        };

        cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY);
        assertThat(loads).hasValue(1);

        // 模拟收到其他实例发出的失效消息（消息体 = 业务键；频道不参与处理逻辑）
        byte[] channel = properties.getCache().getInvalidationChannel()
                .getBytes(StandardCharsets.UTF_8);
        invalidationListener.onMessage(
                new DefaultMessage(channel, key.getBytes(StandardCharsets.UTF_8)), null);

        CacheStats afterBroadcast = cache.stats();
        assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                .containsExactly("shared");
        assertThat(loads).hasValue(1);
        // L1 已清，改由 L2 命中
        assertThat(cache.stats().remoteHits() - afterBroadcast.remoteHits()).isEqualTo(1);
        assertThat(stringRedisTemplate.hasKey(redisKey(key))).isTrue();
    }

    // ==================== 击穿保护 ====================

    @Test
    @DisplayName("击穿降级：锁被其他线程占用时直查数据库且不回填缓存，不阻塞请求线程")
    void lockContentionDegradesToDirectLoad() throws Exception {
        String key = newKey("degrade");
        String lockKey = properties.getCache().getKeyPrefix() + "lock:" + key;

        long originalWait = properties.getCache().getLockWaitSeconds();
        // 设为 0：只尝试一次，用例不会真的等 2 秒
        properties.getCache().setLockWaitSeconds(0);

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            if (lockManager.tryLock(lockKey, 1, 10)) {
                acquired.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lockManager.unlock(lockKey);
                }
            }
        }, "cache-lock-holder");
        holder.start();

        try {
            assertThat(acquired.await(5, TimeUnit.SECONDS))
                    .as("测试线程应能取得缓存回源锁（Redisson 需可用）")
                    .isTrue();

            AtomicInteger loads = new AtomicInteger();
            Supplier<List<String>> loader = () -> {
                loads.incrementAndGet();
                return List.of("direct");
            };

            CacheStats before = cache.stats();
            assertThat(cache.getOrLoad(key, STRING_LIST_TYPE, loader, TEST_POLICY))
                    .containsExactly("direct");
            assertThat(loads).hasValue(1);
            assertThat(cache.stats().degradedLoads() - before.degradedLoads()).isEqualTo(1);
            // 降级路径刻意不写缓存，避免抢锁失败者把可能过期的值固化成一份新缓存
            assertThat(stringRedisTemplate.hasKey(redisKey(key))).isFalse();
        } finally {
            release.countDown();
            holder.join(5_000);
            properties.getCache().setLockWaitSeconds(originalWait);
        }
    }

    // ==================== 业务接入 ====================

    @Test
    @DisplayName("业务接入：分类增删后分类树缓存被正确失效，读到的始终是库中最新数据")
    void categoryWritesEvictTreeCache() {
        // 预热：第一次调用必然回源，第二次应命中 L1
        List<CategoryVO> initial = categoryService.tree();
        assertThat(initial).isNotNull();
        CacheStats afterWarmUp = cache.stats();
        categoryService.tree();
        assertThat(cache.stats().localHits() - afterWarmUp.localHits()).isEqualTo(1);

        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setParentId(0L);
        dto.setName("缓存测试分类-" + UUID.randomUUID());
        dto.setLevel(1);
        dto.setSort(9999);
        dto.setStatus(1);

        Long createdId = categoryService.create(dto);
        try {
            // create 已在提交后失效分类树：这里必须能看到新分类，而不是旧缓存
            assertThat(categoryService.tree())
                    .extracting(CategoryVO::getId)
                    .contains(createdId);
        } finally {
            categoryService.delete(createdId);
            // 逻辑删除会留下 deleted=1 的行；测试数据直接物理删除，避免开发库随测试次数膨胀
            jdbcTemplate.update("DELETE FROM product_category WHERE id = ?", createdId);
        }

        assertThat(categoryService.tree())
                .extracting(CategoryVO::getId)
                .doesNotContain(createdId);
    }

    // ==================== 工具方法 ====================

    private String newKey(String suffix) {
        String key = "test:cache:" + suffix + ":" + UUID.randomUUID();
        keysToClean.add(key);
        return key;
    }

    private String redisKey(String key) {
        return properties.getCache().getKeyPrefix() + key;
    }
}
