package com.fg.loadbalancer.service;

import java.net.InetSocketAddress;

/**
 * 负载均衡器
 */
public interface LoadBalancer {

    /**
     * 根据服务名获取一个可用服务
     *
     * @param serviceName
     * @return
     */
    InetSocketAddress getServiceAddress(String serviceName);
}
