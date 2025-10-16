package com.fg.protection.limiter.service.impl;


import com.fg.protection.limiter.service.RateLimiter;

import java.util.ArrayDeque;

/**
 * 滑动窗口限流：窗口内累计请求不超过 maxRequests，窗口按时间滚动
 */
public class SlidingWindowLimiter implements RateLimiter {

    private final long windowMs;  // 窗口大小
    private final int maxRequests;  // 窗口内最大请求数
    private final ArrayDeque<Long> q = new ArrayDeque<>();  // 记录每次通过的时间戳（ms）

    public SlidingWindowLimiter() {
        this(100, 1000L); // 默认：窗口内最多100次，窗口=1000ms
    }

    public SlidingWindowLimiter(int maxRequests, long windowMs) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMs = windowMs <= 0 ? 1000 : windowMs;
    }

    @Override
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long threshold = now - windowMs;
        // 滚动清理窗口外的旧纪录
        while (!q.isEmpty() && q.peekFirst() < threshold) {
            q.pollFirst();
        }
        if (q.size() < maxRequests) {
            q.addLast(now);
            return true;
        }
        return false;
    }
}
