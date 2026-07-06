package com.dinehub.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
     * 立即按模式删除缓存
     */
    public void evict(String pattern) {
        Set keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 延迟第二删。删除失败仅告警——缓存有回源能力,最终一致由TTL兜底
     */
    public void evictLater(String pattern) {
        scheduler.schedule(() -> {
            try {
                evict(pattern);
                log.debug("delayed second cache eviction done: {}", pattern);
            } catch (Exception e) {
                log.warn("delayed cache eviction failed for {}: {}", pattern, e.getMessage());
            }
        }, doubleDeleteDelayMillis, TimeUnit.MILLISECONDS);
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
        scheduler.shutdownNow();
    }
}
