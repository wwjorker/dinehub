package com.dinehub.ratelimit;

import com.dinehub.constant.MessageConstant;
import com.dinehub.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面:按注解key维护令牌桶,取不到令牌直接拒绝(快速失败)。
 * 单实例内存限流;多实例部署时应换Redis+Lua实现全局配额(当前单机部署,留作演进)。
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 全局开关,压测对照或故障时可关闭
     */
    @Value("${dinehub.rate-limit.enabled:true}")
    private boolean enabled;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }
        TokenBucket bucket = buckets.computeIfAbsent(rateLimit.key(), k -> {
            double capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : rateLimit.permitsPerSecond();
            return new TokenBucket(rateLimit.permitsPerSecond(), capacity);
        });
        if (!bucket.tryAcquire()) {
            log.warn("rate limit hit on {}", rateLimit.key());
            throw new RateLimitException(MessageConstant.TOO_MANY_REQUESTS);
        }
        return joinPoint.proceed();
    }
}
