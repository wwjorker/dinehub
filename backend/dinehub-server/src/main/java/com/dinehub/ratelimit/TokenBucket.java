package com.dinehub.ratelimit;

import java.util.function.LongSupplier;

/**
 * 令牌桶:按时间流逝惰性补充令牌,请求到达时先补后取。
 * 惰性补充避免了后台线程;时间源可注入便于测试。
 */
public class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoTime;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(double permitsPerSecond, double capacity) {
        this(permitsPerSecond, capacity, System::nanoTime);
    }

    public TokenBucket(double permitsPerSecond, double capacity, LongSupplier nanoTime) {
        this.capacity = capacity;
        this.refillPerNano = permitsPerSecond / 1_000_000_000.0;
        this.nanoTime = nanoTime;
        this.tokens = capacity;
        this.lastRefillNanos = nanoTime.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefillNanos = now;
        }
    }
}
