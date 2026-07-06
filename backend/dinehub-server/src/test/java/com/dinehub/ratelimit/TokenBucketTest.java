package com.dinehub.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(10, 3, clock::get);

        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void refillsAtConfiguredRate() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(2, 2, clock::get);
        bucket.tryAcquire();
        bucket.tryAcquire();
        assertFalse(bucket.tryAcquire());

        //2 permits/second -> half a second buys exactly one token
        clock.addAndGet(SECOND / 2);
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void tokensNeverExceedCapacity() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(100, 2, clock::get);

        //a long idle period must not accumulate more than capacity
        clock.addAndGet(60 * SECOND);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void steadyRateIsSustainable() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(10, 10, clock::get);
        for (int i = 0; i < 10; i++) {
            assertTrue(bucket.tryAcquire());
        }
        for (int i = 0; i < 50; i++) {
            clock.addAndGet(SECOND / 10);
            assertTrue(bucket.tryAcquire(), "request " + i + " at steady rate should pass");
        }
    }
}
