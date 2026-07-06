package com.dinehub.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解,基于内存令牌桶(单实例)。
 * capacity 为突发容量,permitsPerSecond 为稳态补充速率。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流维度标识,同一key共享一个令牌桶
     */
    String key();

    /**
     * 每秒补充令牌数(稳态QPS上限)
     */
    double permitsPerSecond();

    /**
     * 桶容量(允许的瞬时突发量),默认等于速率
     */
    double capacity() default -1;
}
