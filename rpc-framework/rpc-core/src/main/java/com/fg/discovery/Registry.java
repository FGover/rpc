package com.fg.discovery;

import com.fg.ServiceConfig;

import java.net.InetSocketAddress;
import java.util.List;

public interface Registry {

    // 注册服务
    void register(ServiceConfig<?> serviceConfig);

    // 注销服务
    void unregister(String serviceName, String group, InetSocketAddress address);

    // 获取服务列表
    List<InetSocketAddress> lookup(String serviceName, String group);
}
