package com.fg.protection.limiter.service.impl;

import com.fg.protection.limiter.service.RateLimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于令牌桶算法的限流器，适用高并发，控制客户端发请求频率或服务端接收频率
 */
public class TokenBucketLimiter implements RateLimiter {
    // 桶的最大容量
    private final long capacity;
    // 每秒新增的令牌数（令牌生成速率）
    private final long refillRatePerSecond;
    // 当前桶钟可用的令牌数，使用原子类保证线程安全
    private final AtomicLong tokens;
    // 上次补充令牌的时间戳（单位：纳秒）
    private volatile long lastRefillTimeNano;


    public TokenBucketLimiter() {
        this(10L, 5L); // 默认：容量10，每秒补5个
    }

    /**
     * 构造方法：设置桶容量和令牌生成速率
     *
     * @param capacity            桶的容量（最大令牌数）
     * @param refillRatePerSecond 每秒生成的令牌数
     */
    public TokenBucketLimiter(long capacity, long refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = new AtomicLong(capacity); // 初始化桶为满
        this.lastRefillTimeNano = System.nanoTime(); // 初始化时间
    }

    /**
     * 尝试获取令牌
     *
     * @return
     */
    @Override
    public synchronized boolean tryAcquire() {
        // 先尝试补充令牌
        refill();
        // 如果桶中还有令牌，就允许访问
        if (tokens.get() > 0) {
            tokens.decrementAndGet(); // 消耗一个令牌
            return true;
        }
        // 没有令牌，拒绝访问
        return false;
    }

    /**
     * 补充令牌逻辑（根据时间计算应添加的令牌数量）
     */
    private void refill() {
        long now = System.nanoTime(); // 当前时间（纳秒）
        long elapsedNanos = now - lastRefillTimeNano; // 距上次补充令牌的时间间隔（纳秒）
        // 计算应补充的令牌数
        long tokensToAdd = (elapsedNanos * refillRatePerSecond) / 1_000_000_000;
        if (tokensToAdd > 0) {
            // 计算新的令牌总数
            long newTokenCount = Math.min(capacity, tokens.get() + tokensToAdd);
            tokens.set(newTokenCount); // 更新令牌数
            lastRefillTimeNano = now; // 更新上次补充令牌的时间
        }
    }

}
