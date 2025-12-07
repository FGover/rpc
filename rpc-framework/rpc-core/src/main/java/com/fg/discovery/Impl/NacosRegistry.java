package com.fg.discovery.Impl;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.discovery.AbstractRegistry;
import com.fg.exception.DiscoveryException;
import com.fg.utils.NetUtil;
import com.fg.utils.nacos.NacosNode;
import com.fg.utils.nacos.NacosUtil;
import com.fg.watcher.NacosServiceChangeListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NacosRegistry extends AbstractRegistry {

    private NamingService namingService;

    // 防重复订阅
    private final ConcurrentHashMap<String, Boolean> subscribed = new ConcurrentHashMap<>();
    // 监听器缓存
    private final ConcurrentHashMap<String, EventListener> listeners = new ConcurrentHashMap<>();

    public NacosRegistry() {
        this.namingService = NacosUtil.createNamingService();
    }

    public NacosRegistry(String connectString, int timeout) {
        this.namingService = NacosUtil.createNamingService(connectString, timeout);
    }

    @Override
    public void register(ServiceConfig<?> service) {
        String serviceName = service.getInterface().getName();
        String group = service.getGroup() != null ? service.getGroup() : "default";
        String ip = NetUtil.getIP();
        int port = RpcBootstrap.getInstance().getConfiguration().getPort();
        // 创建节点
        NacosNode node = new NacosNode(serviceName, group, ip, port, true, 1.0);
        // 注册服务
        NacosUtil.registerInstance(namingService, node);
    }

    @Override
    public void unregister(String serviceName, String group, InetSocketAddress address) {
        NacosUtil.deregisterInstance(namingService, serviceName, group,
                address.getHostString(), address.getPort());
    }

    @Override
    public List<InetSocketAddress> lookup(String serviceName, String group) {
        String targetGroup = group != null ? group : "default";
        String subKey = serviceName + "::" + targetGroup;
        if (subscribed.putIfAbsent(subKey, Boolean.TRUE) == null) {
            NacosServiceChangeListener listener = new NacosServiceChangeListener(serviceName, targetGroup);
            listeners.put(subKey, listener);
            List<InetSocketAddress> addresses = NacosUtil.getInstances(namingService, serviceName, targetGroup, listener)
                    .stream()
                    .map(node -> new InetSocketAddress(node.getIp(), node.getPort()))
                    .toList();
            if (addresses.isEmpty()) {
                throw new DiscoveryException("未找到服务");
            }
            return addresses;
        }
        // 已订阅过，直接取一次
        List<InetSocketAddress> addresses = NacosUtil.getInstances(namingService, serviceName, targetGroup)
                .stream()
                .map(n -> new InetSocketAddress(n.getIp(), n.getPort()))
                .toList();
        if (addresses.isEmpty()) {
            throw new DiscoveryException("未找到服务");
        }
        return addresses;
    }

    // 关闭：取消订阅+关闭客户端
    public void shutdown() {
        listeners.forEach((k, l) -> {
            String[] parts = k.split("::");
            if (parts.length == 2) {
                String svc = parts[0];
                String grp = parts[1];
                try {
                    NacosUtil.unsubscribe(namingService, svc, grp, l);
                    log.info("已取消订阅: {} (group={})", svc, grp);
                } catch (Exception e) {
                    log.warn("取消订阅失败: {} (group={}), err={}", svc, grp, e.getMessage());
                }
            }
        });
        listeners.clear();
        try {
            NacosUtil.close(namingService);
        } catch (Exception e) {
            log.warn("关闭 Nacos 客户端失败: {}", e.getMessage());
        }
    }
}
