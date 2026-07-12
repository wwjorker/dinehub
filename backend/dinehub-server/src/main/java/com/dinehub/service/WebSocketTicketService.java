package com.dinehub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 管理端 WebSocket 一次性凭据：短 TTL、原子消费，避免把长期 JWT 放入 URL。
 */
@Service
public class WebSocketTicketService {

    private static final String KEY_PREFIX = "ws:ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<String> consumeScript;

    public WebSocketTicketService() {
        consumeScript = new DefaultRedisScript<>();
        consumeScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/consume_ws_ticket.lua"));
        consumeScript.setResultType(String.class);
    }

    public String issue(Long employeeId) {
        String ticket = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, String.valueOf(employeeId), TICKET_TTL);
        return ticket;
    }

    public Long consume(String ticket) {
        if (ticket == null || ticket.length() != 36) {
            return null;
        }
        String employeeId = stringRedisTemplate.execute(
                consumeScript, Collections.singletonList(KEY_PREFIX + ticket));
        if (employeeId == null) {
            return null;
        }
        try {
            return Long.valueOf(employeeId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
