package com.dinehub.seckill;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the admission script against a real Redis to prove the properties the
 * unit tests can only assume: atomicity under concurrency, no oversell, one
 * grant per user. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class SeckillLuaScriptTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;
    private static DefaultRedisScript<Long> script;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private Long run(String stockKey, String boughtKey, long userId) {
        return template.execute(script, Arrays.asList(stockKey, boughtKey), String.valueOf(userId));
    }

    @Test
    void grantsExactlyStockThenSellsOut() {
        String stockKey = "t:stock:1", boughtKey = "t:bought:1";
        template.opsForValue().set(stockKey, "3");

        List<Long> results = Arrays.asList(
                run(stockKey, boughtKey, 1), run(stockKey, boughtKey, 2), run(stockKey, boughtKey, 3),
                run(stockKey, boughtKey, 4), run(stockKey, boughtKey, 5));

        assertEquals(Arrays.asList(0L, 0L, 0L, 1L, 1L), results);
        assertEquals("0", template.opsForValue().get(stockKey));
        assertEquals(3, template.opsForSet().size(boughtKey));
    }

    @Test
    void secondAttemptBySameUserIsRejectedWithoutDeduction() {
        String stockKey = "t:stock:2", boughtKey = "t:bought:2";
        template.opsForValue().set(stockKey, "5");

        assertEquals(0L, run(stockKey, boughtKey, 42));
        assertEquals(2L, run(stockKey, boughtKey, 42));
        assertEquals("4", template.opsForValue().get(stockKey));
    }

    @Test
    void missingStockKeyMeansSoldOutNotError() {
        assertEquals(1L, run("t:stock:absent", "t:bought:absent", 1));
    }

    @Test
    void concurrentGrabsNeverOversell() throws InterruptedException {
        String stockKey = "t:stock:3", boughtKey = "t:bought:3";
        int stock = 50, users = 200;
        template.opsForValue().set(stockKey, String.valueOf(stock));

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        AtomicInteger granted = new AtomicInteger();
        for (int u = 1; u <= users; u++) {
            long userId = u;
            pool.submit(() -> {
                try {
                    ready.await();
                    if (run(stockKey, boughtKey, userId) == 0L) {
                        granted.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(stock, granted.get());
        assertEquals("0", template.opsForValue().get(stockKey));
        Set<String> bought = template.opsForSet().members(boughtKey);
        assertEquals(stock, bought.size());
    }
}
