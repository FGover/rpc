package com.fg.loadbalancer.service;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡器
 */
public interface LoadBalancer {

    /**
     * 根据服务名获取一个可用服务
     *
     * @param serviceName
     * @param group
     * @return
     */
    InetSocketAddress getServiceAddress(String serviceName, String group);

    /**
     * 重新平衡负载
     *
     * @param serviceName
     */
    void reLoadBalance(String serviceName, List<InetSocketAddress> addressList);
}
