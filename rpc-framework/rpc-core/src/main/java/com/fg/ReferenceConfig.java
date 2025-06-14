package com.fg;

import com.fg.discovery.Registry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;

@Slf4j
public class ReferenceConfig<T> {

    private Class<T> interfaceRef;

    @Getter
    @Setter
    private Registry registry;

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
                    System.out.println("调用远程方法：" + method.getName());
                    System.out.println(Arrays.toString(args));
                    // 从注册中心寻找一个可用的服务
                    // 传入服务的名字，返回服务的 ip + 端口
                    InetSocketAddress address = registry.lookup(interfaceRef.getName());
                    log.info("找到{}服务，地址：{}:{}", interfaceRef.getName(), address.getHostString(), address.getPort());
                    return null;
                })
        );
    }
}
