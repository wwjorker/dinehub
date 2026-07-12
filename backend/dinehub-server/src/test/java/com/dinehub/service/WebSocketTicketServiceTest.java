package com.dinehub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTicketServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private WebSocketTicketService service;

    @BeforeEach
    void setUp() {
        service = new WebSocketTicketService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void issuedTicketHasShortTtlAndEmployeeIdentity() {
        String ticket = service.issue(17L);

        assertEquals(36, ticket.length());
        verify(valueOperations).set(eq("ws:ticket:" + ticket), eq("17"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void ticketIsConsumedThroughAtomicScript() {
        String ticket = "12345678-1234-1234-1234-123456789012";
        when(redisTemplate.execute(any(RedisScript.class),
                argThat(keys -> keys.equals(Collections.singletonList("ws:ticket:" + ticket))))).thenReturn("23");

        assertEquals(23L, service.consume(ticket));
    }

    @Test
    void malformedOrMissingTicketIsRejected() {
        assertNull(service.consume("short"));

        String ticket = "12345678-1234-1234-1234-123456789012";
        when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(null);
        assertNull(service.consume(ticket));
    }
}
