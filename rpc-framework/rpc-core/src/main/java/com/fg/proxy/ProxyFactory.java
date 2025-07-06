package com.fg.proxy;

import com.fg.ReferenceConfig;
import com.fg.RpcBootstrap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyFactory {

    public static Map<Class<?>, Object> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> clazz) {
        if (cache.get(clazz) != null) {
            return (T) cache.get(clazz);
        }
        ReferenceConfig<T> reference = new ReferenceConfig<>();
        reference.setInterface(clazz);
        RpcBootstrap.getInstance().group("main").reference(reference);
        T t = reference.get();
        cache.put(clazz, t);
        return t;
    }
}
