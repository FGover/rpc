package com.fg.protection;

import com.fg.RpcBootstrap;

import java.util.concurrent.ConcurrentHashMap;

public class BreakerRegistry {

    private static final ConcurrentHashMap<String, CircuitBreaker> REG = new ConcurrentHashMap<>();

    public static CircuitBreaker get(String serviceName, String group) {
        String normGroup = (group == null || group.isBlank())
                ? RpcBootstrap.getInstance().getConfiguration().getGroup()
                : group;
        String key = serviceName + "::" + normGroup;
        // 阈值从全局配置取
        return REG.computeIfAbsent(key, k -> new CircuitBreaker(3, 2, 5000));
    }

    private BreakerRegistry() {
    }
}
