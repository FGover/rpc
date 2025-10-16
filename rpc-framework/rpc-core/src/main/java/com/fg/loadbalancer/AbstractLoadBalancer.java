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

    /**
     * 通过选择器选取服务地址
     *
     * @param serviceName
     * @param group
     * @return
     */
    @Override
    public InetSocketAddress getServiceAddress(String serviceName, String group) {
        String normGroup = (group == null || group.isBlank())
                ? RpcBootstrap.getInstance().getConfiguration().getGroup()
                : group;
        final String cacheKey = serviceName + "::" + normGroup;
        // 优先从缓存中获取选择器，如果缓存中没有，则创建一个选择器
        Selector selector = selectors.computeIfAbsent(cacheKey, k -> {
            List<InetSocketAddress> serviceList = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig()
                    .getRegistry().lookup(serviceName, normGroup);
            if (serviceList == null || serviceList.isEmpty()) {
                log.error("{}服务的列表为空", serviceName);
                throw new RuntimeException("Service not found: " + serviceName);
            }
            return getSelector(serviceList);
        });
        return selector.selectServiceInstance();
    }

    /**
     * 重新平衡负载，更新服务对应的选择器（负载均衡节点列表）
     *
     * @param serviceName
     */
    @Override
    public synchronized void reLoadBalance(String serviceName, List<InetSocketAddress> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            log.warn("服务[{}]的地址列表为空，跳过负载均衡刷新", serviceName);
            return;
        }
        // 根据最新服务列表重建选择器存入缓存
        String normGroup = RpcBootstrap.getInstance().getConfiguration().getGroup();
        final String cacheKey = serviceName + "::" + normGroup;
        selectors.put(cacheKey, getSelector(addressList));
        log.info("服务[{}]的负载均衡已刷新，新的服务列表为: {}", serviceName, addressList);
    }

    /**
     * 获取一个选择器
     *
     * @param serviceList
     * @return
     */
    protected abstract Selector getSelector(List<InetSocketAddress> serviceList);
}
