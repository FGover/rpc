package com.fg.protection.limiter.service;

public interface RateLimiter {
    /**
     * 尝试获取一个令牌/请求许可
     *
     * @return 是否允许请求
     */
    boolean tryAcquire();
}
