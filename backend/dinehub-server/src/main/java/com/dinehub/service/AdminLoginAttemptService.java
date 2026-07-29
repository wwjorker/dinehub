package com.dinehub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class AdminLoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String PREFIX = "auth:admin:fail:";

    private final StringRedisTemplate redisTemplate;

    public AdminLoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlocked(String username, String remoteAddress) {
        try {
            String value = redisTemplate.opsForValue().get(key(username, remoteAddress));
            return value != null && Integer.parseInt(value) >= MAX_FAILURES;
        } catch (Exception ex) {
            log.warn("admin login protection unavailable, failing open");
            return false;
        }
    }

    public void recordFailure(String username, String remoteAddress) {
        try {
            String key = key(username, remoteAddress);
            Long failures = redisTemplate.opsForValue().increment(key);
            if (failures != null && failures == 1) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (Exception ex) {
            log.warn("failed to record admin login attempt");
        }
    }

    public void clear(String username, String remoteAddress) {
        try {
            redisTemplate.delete(key(username, remoteAddress));
        } catch (Exception ex) {
            log.warn("failed to clear admin login attempts");
        }
    }

    private String key(String username, String remoteAddress) {
        String identity = String.valueOf(username).trim().toLowerCase() + "|" + remoteAddress;
        return PREFIX + DigestUtils.md5DigestAsHex(identity.getBytes(StandardCharsets.UTF_8));
    }
}
