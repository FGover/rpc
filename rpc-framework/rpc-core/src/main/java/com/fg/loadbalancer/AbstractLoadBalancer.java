package com.fg.loadbalancer;

import com.fg.RpcBootstrap;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.loadbalancer.service.Selector;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractLoadBalancer implements LoadBalancer {

    // 每个服务名对应一个选择器
    private final Map<String, Selector> selectors = new ConcurrentHashMap<>();

    @Override
    public InetSocketAddress getServiceAddress(String serviceName) {
        // 优先从缓存器中获取一个选择器
        Selector selector = selectors.get(serviceName);
        // 如果缓存器中没有，则创建一个选择器
        if (selector == null) {
            List<InetSocketAddress> serviceList = RpcBootstrap.getInstance().getRegistry().lookup(serviceName);
            if (serviceList == null || serviceList.isEmpty()) {
                log.error("{}服务的列表为空", serviceName);
                throw new RuntimeException("Service not found: " + serviceName);
            }
            // 通过算法选取节点
            selector = getSelector(serviceList);
            // 将选择器放入缓存器中
            selectors.put(serviceName, selector);
        }
        return selector.selectServiceInstance();
    }

    /**
     * 获取一个选择器
     *
     * @param serviceList
     * @return
     */
    protected abstract Selector getSelector(List<InetSocketAddress> serviceList);
}
