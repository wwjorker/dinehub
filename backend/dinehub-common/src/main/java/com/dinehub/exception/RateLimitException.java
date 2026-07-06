package com.dinehub.exception;

/**
 * 触发限流
 */
public class RateLimitException extends BaseException {

    public RateLimitException(String msg) {
        super(msg);
    }
}
