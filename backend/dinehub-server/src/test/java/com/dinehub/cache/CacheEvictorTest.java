package com.dinehub.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheEvictorTest {

    @Mock
    private RedisTemplate redisTemplate;

    @InjectMocks
    private CacheEvictor cacheEvictor;

    @Test
    void doubleDeleteEvictsBeforeAndAfterDbWrite() {
        ReflectionTestUtils.setField(cacheEvictor, "doubleDeleteDelayMillis", 50L);
        when(redisTemplate.keys("dish_*")).thenReturn(Collections.singleton("dish_1"));

        StringBuilder order = new StringBuilder();
        cacheEvictor.doubleDelete("dish_*", () -> order.append("db"));

        //first eviction is synchronous and precedes the db write's completion record
        verify(redisTemplate, times(1)).delete(Collections.singleton("dish_1"));
        org.junit.jupiter.api.Assertions.assertEquals("db", order.toString());

        //second eviction lands asynchronously after the configured delay
        verify(redisTemplate, timeout(TimeUnit.SECONDS.toMillis(5)).times(2))
                .delete(Collections.singleton("dish_1"));
    }
}
