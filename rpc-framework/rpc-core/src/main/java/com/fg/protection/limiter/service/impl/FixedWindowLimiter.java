package com.fg.protection.limiter.service.impl;

import com.fg.protection.limiter.service.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 固定窗口限流：窗口内最多通过 qps 个请求，窗口结束后重置计数
 */
public class FixedWindowLimiter implements RateLimiter {

    private final long qps;
    private final long windowMs;
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong counter = new AtomicLong(0);

    public FixedWindowLimiter() {
        this(100L, 1000L); // 默认：qps=100, 窗口=1000ms
    }

    public FixedWindowLimiter(long qps, long windowMs) {
        this.qps = qps;
        this.windowMs = windowMs;
    }

    @Override
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (now - windowStart.get() >= windowMs) {
            windowStart.set(now);
            counter.set(0);
        }
        if (counter.get() < qps) {
            counter.incrementAndGet();
            return true;
        }
        return false;
    }
}
