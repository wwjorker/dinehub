package com.dinehub.cache;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CacheEvictorTest {

    @Test
    void doubleDeleteEvictsBeforeAndAfterDbWrite() {
        CacheEvictor evictor = spy(new CacheEvictor());
        ReflectionTestUtils.setField(evictor, "doubleDeleteDelayMillis", 50L);

        //记录事件顺序:首删必须先于DB写,第二删在延迟后异步到达
        List<String> events = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            events.add("evict");
            return null;
        }).when(evictor).evict("dish_*");

        evictor.doubleDelete("dish_*", () -> events.add("db"));

        verify(evictor, timeout(5000).times(2)).evict("dish_*");
        assertEquals("evict", events.get(0));
        assertEquals("db", events.get(1));
        assertEquals("evict", events.get(2));
    }
}
