package com.tom.tomshop.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 跨实例缓存失效监听器。
 *
 * <p><b>要解决的问题</b>：L1 在各自进程的 JVM 堆里。A 实例更新数据后只能删掉自己的 L1 和共享的 Redis，
 * B 实例的 L1 仍然拿着旧值，直到本地 TTL 到期。多实例部署时这就是「刷新页面有时新有时旧」的来源。
 *
 * <p>解法是 Redis pub/sub：失效方往频道里发一个键名，所有实例（包括自己）收到后清掉对应的 L1。
 * 消息体只放键名 —— 不传数据本身，避免「广播里的值也可能旧」这种二次一致性问题。
 *
 * <p>pub/sub 是<b>至多一次</b>投递：订阅端断线期间的消息会丢。因此它只是让失效「及时」，
 * 正确性的最终兜底仍是 L1 的短 TTL —— 丢掉一条消息的代价是最多陈旧几十秒。
 *
 * @author Tom
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private final MultiLevelCache cache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        if (key.isEmpty()) {
            return;
        }
        cache.evictLocal(key);
        log.debug("[缓存] 收到失效广播，已清理本地：{}", key);
    }
}
