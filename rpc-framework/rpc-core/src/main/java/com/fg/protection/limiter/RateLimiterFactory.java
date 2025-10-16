package com.fg.protection.limiter;

import com.fg.protection.limiter.service.RateLimiter;
import com.fg.protection.limiter.service.impl.FixedWindowLimiter;
import com.fg.protection.limiter.service.impl.LeakyBucketLimiter;
import com.fg.protection.limiter.service.impl.SlidingWindowLimiter;
import com.fg.protection.limiter.service.impl.TokenBucketLimiter;

import java.util.Map;

public class RateLimiterFactory {

    public static RateLimiter create(String type, long p1, long p2) {
        String t = (type == null || type.isBlank()) ? "token" : type.toLowerCase();
        return switch (t) {
            case "fixed" -> new FixedWindowLimiter(p1, p2 > 0 ? p2 : 1000);
            case "sliding" -> new SlidingWindowLimiter((int) p1, p2 > 0 ? p2 : 1000);
            case "leaky" -> new LeakyBucketLimiter(p1, p2);
            case "token" -> new TokenBucketLimiter(p1, p2);
            default -> new TokenBucketLimiter(p1, p2);
        };
    }
    public static RateLimiter create(Map<String, String> conf) {
        if (conf == null) {
            return new TokenBucketLimiter(10, 5);
        }
        String type = conf.getOrDefault("type", "token").toLowerCase();
        switch (type) {
            case "fixed": {
                long qps = parse(conf.get("qps"), 100L);
                long windowMs = parse(conf.get("windowMs"), 1000L);
                return new FixedWindowLimiter(qps, windowMs);
            }
            case "sliding": {
                int maxRequests = (int) parse(conf.get("maxRequests"), 100L);
                long windowMs = parse(conf.get("windowMs"), 1000L);
                return new SlidingWindowLimiter(maxRequests, windowMs);
            }
            case "leaky": {
                long capacity = parse(conf.get("capacity"), 100L);
                long leakPerSecond = parse(conf.get("leakPerSecond"), 50L);
                return new LeakyBucketLimiter(capacity, leakPerSecond);
            }
            case "token":
            default: {
                long capacity = parse(conf.get("capacity"), 10L);
                long refillPerSecond = parse(conf.get("refillPerSecond"), 5L);
                return new TokenBucketLimiter(capacity, refillPerSecond);
            }
        }
    }

    private static long parse(String v, long def) {
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
