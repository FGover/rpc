package com.fg.protection.limiter.service.impl;

import com.fg.protection.limiter.service.RateLimiter;


/**
 * 漏桶限流：按恒定速率出水；请求进桶，若满则丢弃
 */
public class LeakyBucketLimiter implements RateLimiter {

    private final long capacity;  // 桶容量
    private final double leakPerMs;  // 每毫秒出水速率
    private double water;  // 当前水量
    private long lastTs;

    public LeakyBucketLimiter() {
        this(100L, 50L); // 默认：容量100，漏速50/s
    }

    public LeakyBucketLimiter(long capacity, long leakPerSecond) {
        this.capacity = capacity;
        this.leakPerMs = leakPerSecond <= 0 ? 0.0 : (leakPerSecond / 1000.0);
        this.lastTs = System.currentTimeMillis();
    }

    @Override
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        double leaked = (now - lastTs) * leakPerMs;
        water = Math.max(0, water - leaked);
        lastTs = now;
        if (water + 1 <= capacity) {
            water += 1;
            return true;
        }
        return false;
    }
}
