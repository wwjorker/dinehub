package com.dinehub.seckill;

import com.dinehub.mq.SeckillMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillRedisLedger {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String BOUGHT_KEY_PREFIX = "seckill:bought:";
    public static final String REQUEST_KEY_PREFIX = "seckill:req:";
    public static final String ACTIVE_KEY_PREFIX = "seckill:active:";
    public static final String COMPENSATED_KEY_PREFIX = "seckill:compensated:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> admissionScript;
    private final DefaultRedisScript<Long> claimScript;
    private final DefaultRedisScript<Long> publishResultScript;
    private final DefaultRedisScript<Long> markCompensatingScript;
    private final DefaultRedisScript<Long> compensateScript;

    public SeckillRedisLedger(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.admissionScript = script("lua/seckill.lua");
        this.claimScript = script("lua/seckill_publish_claim.lua");
        this.publishResultScript = script("lua/seckill_publish_result.lua");
        this.markCompensatingScript = script("lua/seckill_mark_compensating.lua");
        this.compensateScript = script("lua/seckill_compensate.lua");
    }

    private DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    public Long admit(Long couponId, Long userId, String requestId, Duration ttl) {
        long now = System.currentTimeMillis();
        return redisTemplate.execute(admissionScript,
                Arrays.asList(stockKey(couponId), boughtKey(couponId), requestKey(requestId), activeKey(couponId)),
                String.valueOf(userId), String.valueOf(couponId), requestId, String.valueOf(now),
                String.valueOf(Math.max(1L, ttl.getSeconds())));
    }

    public long claimPublish(SeckillMessage message, Duration confirmTimeout) {
        long now = System.currentTimeMillis();
        Long attempt = redisTemplate.execute(claimScript,
                Arrays.asList(requestKey(message.getRequestId()), activeKey(message.getCouponId())),
                message.getRequestId(), String.valueOf(now), String.valueOf(now + confirmTimeout.toMillis()));
        return attempt == null ? -1L : attempt;
    }

    public void markPublished(SeckillMessage message, int attempt, Duration settlementTimeout) {
        applyPublishResult(message, attempt, "PUBLISHED",
                System.currentTimeMillis() + settlementTimeout.toMillis(), "");
    }

    public void markPublishFailed(SeckillMessage message, int attempt, Duration retryDelay, String error) {
        applyPublishResult(message, attempt, "PENDING",
                System.currentTimeMillis() + retryDelay.toMillis(), trim(error));
    }

    private void applyPublishResult(SeckillMessage message, int attempt, String status, long nextCheck, String error) {
        redisTemplate.execute(publishResultScript,
                Arrays.asList(requestKey(message.getRequestId()), activeKey(message.getCouponId())),
                message.getRequestId(), String.valueOf(attempt), status, String.valueOf(nextCheck), error);
    }

    public void markCompensating(SeckillMessage message, String error) {
        redisTemplate.execute(markCompensatingScript,
                Arrays.asList(requestKey(message.getRequestId()), activeKey(message.getCouponId())),
                message.getRequestId(), String.valueOf(System.currentTimeMillis()), trim(error));
    }

    public boolean compensate(SeckillMessage message, boolean keepBought, Duration markerTtl) {
        Long stockTtl = redisTemplate.getExpire(stockKey(message.getCouponId()), TimeUnit.SECONDS);
        long markerSeconds = Math.max(1L, markerTtl.getSeconds());
        if (stockTtl != null && stockTtl > markerSeconds) {
            markerSeconds = stockTtl;
        }
        Long result = redisTemplate.execute(compensateScript,
                Arrays.asList(compensatedKey(message.getRequestId()), stockKey(message.getCouponId()),
                        boughtKey(message.getCouponId())),
                String.valueOf(message.getUserId()), String.valueOf(markerSeconds),
                keepBought ? "1" : "0");
        return result != null && result == 1L;
    }

    public PendingRequest get(String requestId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(requestKey(requestId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return PendingRequest.builder()
                    .requestId(requestId)
                    .userId(Long.valueOf(String.valueOf(values.get("userId"))))
                    .couponId(Long.valueOf(String.valueOf(values.get("couponId"))))
                    .status(String.valueOf(values.get("status")))
                    .attempt(Integer.valueOf(String.valueOf(values.get("attempt"))))
                    .createdAt(Long.valueOf(String.valueOf(values.get("createdAt"))))
                    .lastError(String.valueOf(values.get("lastError")))
                    .build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Set<String> dueRequestIds(Long couponId, long nowMillis, long limit) {
        Set<String> values = redisTemplate.opsForZSet()
                .rangeByScore(activeKey(couponId), 0, nowMillis, 0, limit);
        return values == null ? Collections.emptySet() : new LinkedHashSet<>(values);
    }

    public void cleanup(SeckillMessage message) {
        redisTemplate.opsForZSet().remove(activeKey(message.getCouponId()), message.getRequestId());
        redisTemplate.delete(requestKey(message.getRequestId()));
    }

    public boolean isCompensated(String requestId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(compensatedKey(requestId)));
    }

    public void removeOrphan(Long couponId, String requestId) {
        redisTemplate.opsForZSet().remove(activeKey(couponId), requestId);
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    public static String stockKey(Long couponId) {
        return STOCK_KEY_PREFIX + couponId;
    }

    public static String boughtKey(Long couponId) {
        return BOUGHT_KEY_PREFIX + couponId;
    }

    public static String requestKey(String requestId) {
        return REQUEST_KEY_PREFIX + requestId;
    }

    public static String activeKey(Long couponId) {
        return ACTIVE_KEY_PREFIX + couponId;
    }

    public static String compensatedKey(String requestId) {
        return COMPENSATED_KEY_PREFIX + requestId;
    }

    @lombok.Data
    @lombok.Builder
    public static class PendingRequest {
        private String requestId;
        private Long userId;
        private Long couponId;
        private String status;
        private Integer attempt;
        private Long createdAt;
        private String lastError;

        public SeckillMessage toMessage() {
            return SeckillMessage.builder()
                    .requestId(requestId)
                    .userId(userId)
                    .couponId(couponId)
                    .build();
        }
    }
}
