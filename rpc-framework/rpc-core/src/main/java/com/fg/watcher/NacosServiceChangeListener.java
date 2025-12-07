package com.fg.watcher;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fg.NettyBootstrapInitializer;
import com.fg.RpcBootstrap;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 动态感知服务上下线监听器
 */
@Slf4j
public class NacosServiceChangeListener implements EventListener {

    private static final ExecutorService EVENT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "nacos-service-change-worker");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<InetSocketAddress, Boolean> CONNECTING = new ConcurrentHashMap<>();

    private final String serviceName;
    private final String group;

    public NacosServiceChangeListener(String serviceName, String group) {
        this.serviceName = serviceName;
        this.group = group;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof NamingEvent namingEvent)) {
            return;
        }
        // 拷贝一份数据，异步处理，避免阻塞回调线程
        List<InetSocketAddress> latest = namingEvent.getInstances().stream()
                .filter(Instance::isHealthy)
                .map(i -> new InetSocketAddress(i.getIp(), i.getPort()))
                .toList();
        EVENT_EXECUTOR.submit(() -> {
            try {
                log.info("监听到服务[{}]实例变化（group={}），将重新加载：{}", serviceName, group, latest);
                // 新增节点：建立连接并缓存
                for (InetSocketAddress address : latest) {
                    // 已有连接直接跳过
                    if (RpcBootstrap.CHANNEL_MAP.containsKey(address)) {
                        continue;
                    }
                    // 正在连接的跳过（占位去重）
                    if (CONNECTING.putIfAbsent(address, Boolean.TRUE) != null) {
                        continue;
                    }
                    log.info("新增服务节点[{}:{}]，尝试连接上线", address.getHostName(), address.getPort());
                    try {
                        Channel channel = NettyBootstrapInitializer.getBootstrap().connect(address).sync().channel();
                        RpcBootstrap.CHANNEL_MAP.put(address, channel);
                        log.info("成功连接到服务节点[{}:{}]，服务已成功上线", address.getHostName(), address.getPort());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("连接中断: {}:{}", address.getHostString(), address.getPort());
                    } catch (Exception e) {
                        log.warn("连接失败: {}:{}, err={}", address.getHostString(), address.getPort(), e.getMessage());
                    } finally {
                        // 无论成功或失败，释放占位
                        CONNECTING.remove(address);
                    }
                }
                // 移除下线节点：关闭并剔除
                RpcBootstrap.CHANNEL_MAP.keySet().removeIf(existing -> {
                    if (!latest.contains(existing)) {
                        Channel channel = RpcBootstrap.CHANNEL_MAP.get(existing);
                        if (channel != null && channel.isActive()) {
                            channel.close();
                        }
                        CONNECTING.remove(existing);
                        log.info("服务节点[{}:{}]已下线，已移除并关闭连接", existing.getHostName(), existing.getPort());
                        return true;
                    }
                    return false;
                });
                // 刷新负载均衡器
                String normGroup = (group == null || group.isBlank())
                        ? RpcBootstrap.getInstance().getConfiguration().getGroup()
                        : group;
                RpcBootstrap.getInstance().getConfiguration().getLoadBalancer().reLoadBalance(serviceName, normGroup, latest);
                log.info("刷新负载均衡器，服务[{}]当前列表为：{}", serviceName, latest);
            } catch (Exception ex) {
                log.warn("处理实例变化异常: service={}, group={}, err={}", serviceName, group, ex.getMessage());
            }
        });
    }
}
