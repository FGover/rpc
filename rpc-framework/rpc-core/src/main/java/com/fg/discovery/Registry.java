package com.fg.discovery;

import com.fg.ServiceConfig;

import java.net.InetSocketAddress;

public interface Registry {

    // 注册服务
    void register(ServiceConfig<?> serviceConfig);

    // 查找服务
    InetSocketAddress lookup(String serviceName);
}
