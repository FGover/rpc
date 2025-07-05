package com.fg;

import com.fg.discovery.Registry;
import com.fg.proxy.handler.RpcConsumerInvocationHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;


@Slf4j
public class ReferenceConfig<T> {

    private Class<T> interfaceRef;

    @Getter
    @Setter
    private Registry registry;
    @Getter
    @Setter
    private String group;

    public Class<T> getInterface() {
        return interfaceRef;
    }

    public void setInterface(Class<T> interfaceRef) {
        this.interfaceRef = interfaceRef;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        RpcConsumerInvocationHandler handler = new RpcConsumerInvocationHandler(registry, interfaceRef, group);
        // 动态代理
        return (T) Proxy.newProxyInstance(interfaceRef.getClassLoader(), new Class[]{interfaceRef}, handler);
    }

}
