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
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeckillRedisLedger {

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String BOUGHT_KEY_PREFIX = "seckill:bought:";
    public static final String REQUEST_KEY_PREFIX = "seckill:req:";
    public static final String ACTIVE_KEY_PREFIX = "seckill:active:";
    public static final String COMPENSATED_KEY_PREFIX = "seckill:compensated:";
    public static final String REBUILD_LOCK_KEY_PREFIX = "seckill:rebuild:";
    public static final String TERMINAL_INFLIGHT_KEY_PREFIX = "seckill:terminal:inflight:";

    private final StringRedisTemplate redisTemplate;
    private final SeckillMetrics metrics;
    private final DefaultRedisScript<Long> admissionScript;
    private final DefaultRedisScript<Long> claimScript;
    private final DefaultRedisScript<Long> publishResultScript;
    private final DefaultRedisScript<Long> markCompensatingScript;
    private final DefaultRedisScript<Long> compensateScript;
    private final DefaultRedisScript<Long> terminalBeginScript;
    private final DefaultRedisScript<Long> terminalEndScript;
    private final DefaultRedisScript<Long> unlockScript;
    private final DefaultRedisScript<Long> renewLockScript;
    private final DefaultRedisScript<Long> rebuildCommitScript;

    public SeckillRedisLedger(StringRedisTemplate redisTemplate, SeckillMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.admissionScript = script("lua/seckill.lua");
        this.claimScript = script("lua/seckill_publish_claim.lua");
        this.publishResultScript = script("lua/seckill_publish_result.lua");
        this.markCompensatingScript = script("lua/seckill_mark_compensating.lua");
        this.compensateScript = script("lua/seckill_compensate.lua");
        this.terminalBeginScript = script("lua/seckill_terminal_begin.lua");
        this.terminalEndScript = script("lua/seckill_terminal_end.lua");
        this.unlockScript = script("lua/seckill_unlock.lua");
        this.renewLockScript = script("lua/seckill_renew_lock.lua");
        this.rebuildCommitScript = script("lua/seckill_rebuild_commit.lua");
    }

    private DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    public Long admit(Long couponId, Long userId, String requestId, Duration ttl) {
        long now = System.currentTimeMillis();
        Long result = metrics.recordAdmission(() -> redisTemplate.execute(admissionScript,
                Arrays.asList(stockKey(couponId), boughtKey(couponId), requestKey(requestId), activeKey(couponId),
                        rebuildLockKey(couponId)),
                String.valueOf(userId), String.valueOf(couponId), requestId, String.valueOf(now),
                String.valueOf(Math.max(1L, ttl.getSeconds()))));
        if (result != null && result == 0L) {
            metrics.admitted();
        }
        return result;
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

    public CompensationOutcome compensate(SeckillMessage message, boolean keepBought, Duration markerTtl) {
        Long stockTtl = redisTemplate.getExpire(stockKey(message.getCouponId()), TimeUnit.SECONDS);
        long markerSeconds = Math.max(1L, markerTtl.getSeconds());
        if (stockTtl != null && stockTtl > markerSeconds) {
            markerSeconds = stockTtl;
        }
        Long result = redisTemplate.execute(compensateScript,
                Arrays.asList(compensatedKey(message.getRequestId()), stockKey(message.getCouponId()),
                        boughtKey(message.getCouponId()), rebuildLockKey(message.getCouponId())),
                String.valueOf(message.getUserId()), String.valueOf(markerSeconds),
                keepBought ? "1" : "0");
        if (result != null && result == -1L) {
            return CompensationOutcome.DEFERRED;
        }
        return result != null && result == 1L
                ? CompensationOutcome.APPLIED : CompensationOutcome.ALREADY_APPLIED;
    }

    public boolean beginTerminalTransition(Long couponId) {
        Long result = redisTemplate.execute(terminalBeginScript,
                Arrays.asList(rebuildLockKey(couponId), terminalInflightKey(couponId)), "60");
        return result != null && result > 0;
    }

    public void endTerminalTransition(Long couponId) {
        redisTemplate.execute(terminalEndScript, Collections.singletonList(terminalInflightKey(couponId)));
    }

    public String acquireRebuildLock(Long couponId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(rebuildLockKey(couponId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public boolean awaitTerminalQuiescence(Long couponId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            String value = redisTemplate.opsForValue().get(terminalInflightKey(couponId));
            if (value == null || Long.parseLong(value) <= 0) {
                return true;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < deadline);
        return false;
    }

    public void releaseRebuildLock(Long couponId, String token) {
        redisTemplate.execute(unlockScript, Collections.singletonList(rebuildLockKey(couponId)), token);
    }

    public boolean renewRebuildLock(Long couponId, String token, Duration ttl) {
        Long result = redisTemplate.execute(renewLockScript,
                Collections.singletonList(rebuildLockKey(couponId)), token,
                String.valueOf(Math.max(1L, ttl.toMillis())));
        return result != null && result == 1L;
    }

    /**
     * Builds the potentially large bought-user snapshot under a temporary key,
     * then atomically verifies the lock token and swaps stock/bought together.
     * A stale rebuild owner can never publish its snapshot.
     */
    public boolean replaceLedgerIfLockOwned(Long couponId, String token, int available,
                                            Set<Long> protectedUsers, Duration ttl, Duration lease) {
        String snapshotKey = "seckill:rebuild:snapshot:" + couponId + ":" + token;
        redisTemplate.delete(snapshotKey);
        try {
            if (protectedUsers != null && !protectedUsers.isEmpty()) {
                List<String> members = new ArrayList<>(protectedUsers.size());
                for (Long userId : protectedUsers) {
                    members.add(String.valueOf(userId));
                    if (members.size() == 1000) {
                        redisTemplate.opsForSet().add(snapshotKey, members.toArray(new String[0]));
                        redisTemplate.expire(snapshotKey, ttl);
                        members.clear();
                    }
                }
                if (!members.isEmpty()) {
                    redisTemplate.opsForSet().add(snapshotKey, members.toArray(new String[0]));
                    redisTemplate.expire(snapshotKey, ttl);
                }
            }
            if (!renewRebuildLock(couponId, token, lease)) {
                return false;
            }
            Long result = redisTemplate.execute(rebuildCommitScript,
                    Arrays.asList(rebuildLockKey(couponId), stockKey(couponId),
                            boughtKey(couponId), snapshotKey),
                    token, String.valueOf(available), String.valueOf(Math.max(1L, ttl.getSeconds())));
            return result != null && result == 1L;
        } finally {
            redisTemplate.delete(snapshotKey);
        }
    }

    public Set<String> allActiveRequestIds(Long couponId) {
        Set<String> values = redisTemplate.opsForZSet().range(activeKey(couponId), 0, -1);
        return values == null ? Collections.emptySet() : new LinkedHashSet<>(values);
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

    public Integer availableStock(Long couponId) {
        String value = redisTemplate.opsForValue().get(stockKey(couponId));
        return value == null ? null : Integer.valueOf(value);
    }

    public Long protectedUserCount(Long couponId) {
        return redisTemplate.opsForSet().size(boughtKey(couponId));
    }

    public boolean isUserProtected(Long couponId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet()
                .isMember(boughtKey(couponId), String.valueOf(userId)));
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

    public static String rebuildLockKey(Long couponId) {
        return REBUILD_LOCK_KEY_PREFIX + couponId;
    }

    public static String terminalInflightKey(Long couponId) {
        return TERMINAL_INFLIGHT_KEY_PREFIX + couponId;
    }

    public enum CompensationOutcome {
        APPLIED, ALREADY_APPLIED, DEFERRED
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
                    .admittedAt(createdAt)
                    .build();
        }
    }
}
