package com.dinehub.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * 缓存清理器,实现延迟双删:
 * 写库前删一次,写库后延迟再删一次,覆盖"首删与DB提交之间读请求把旧值
 * 回填缓存"的窗口。延迟量应略大于一次读请求的耗时(含主从延迟)。
 */
@Component
@Slf4j
public class CacheEvictor {

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${dinehub.cache.double-delete-delay-millis:500}")
    private long doubleDeleteDelayMillis;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-evict-scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * 立即按模式删除缓存。用SCAN渐进遍历代替KEYS,避免大键空间下阻塞Redis
     */
    public void evict(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection().scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 延迟第二删。删除失败仅告警——缓存有回源能力,最终一致由TTL兜底
     */
    public void evictLater(String pattern) {
        try {
            scheduler.schedule(() -> {
                try {
                    evict(pattern);
                    log.debug("delayed second cache eviction done: {}", pattern);
                } catch (Exception e) {
                    log.warn("delayed cache eviction failed for {}: {}", pattern, e.getMessage());
                }
            }, doubleDeleteDelayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            log.warn("delayed cache eviction rejected during shutdown: {}", pattern);
        }
    }

    /**
     * 延迟双删地执行一次DB写操作
     */
    public void doubleDelete(String pattern, Runnable dbWrite) {
        evict(pattern);
        dbWrite.run();
        evictLater(pattern);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        long waitMillis = Math.min(3000L, Math.max(500L, doubleDeleteDelayMillis + 500L));
        try {
            if (!scheduler.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}
