package com.fg.discovery;

import com.fg.ServiceConfig;

import java.net.InetSocketAddress;
import java.util.List;

public interface Registry {

    // 注册服务
    void register(ServiceConfig<?> serviceConfig);

    // 获取服务列表
    List<InetSocketAddress> lookup(String serviceName);
}
