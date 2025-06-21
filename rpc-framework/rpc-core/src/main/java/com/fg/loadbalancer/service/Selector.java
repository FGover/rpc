package com.fg.loadbalancer.service;


import java.net.InetSocketAddress;

public interface Selector {
    /**
     * 根据服务列表执行算法获取一个服务节点
     *
     * @return
     */
    InetSocketAddress selectServiceInstance();
}
