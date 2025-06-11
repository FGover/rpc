package com.fg;

import java.lang.reflect.Proxy;

public class ReferenceConfig<T> {

    private Class<T> interfaceRef;

    public Class<T> getInterface() {
        return interfaceRef;
    }

    public void setInterface(Class<T> interfaceRef) {
        this.interfaceRef = interfaceRef;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        // 动态代理
        return (T) Proxy.newProxyInstance(
                interfaceRef.getClassLoader(),
                new Class[]{interfaceRef},
                ((proxy, method, args) -> {
                    System.out.println("hello proxy");
                    return null;
                })
        );
    }
}
