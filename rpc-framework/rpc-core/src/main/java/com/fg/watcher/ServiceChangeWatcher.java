package com.fg.watcher;

import com.fg.NettyBootstrapInitializer;
import com.fg.RpcBootstrap;
import com.fg.discovery.Registry;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 动态感知服务上下线监听器，用于监听ZooKeeper子节点的变化
 */
@Slf4j
public class ServiceChangeWatcher implements Watcher {
    // 服务名称
    private final String serviceName;

    public ServiceChangeWatcher(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        // 子节点变化
        if (watchedEvent.getType() == Event.EventType.NodeChildrenChanged) {
            log.info("监听到服务[{}]节点上下线，将重新拉取服务列表", serviceName);
            Registry registry = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig().getRegistry();
            // 重新拉取服务列表
            List<InetSocketAddress> addressList = registry.lookup(serviceName, RpcBootstrap.getInstance()
                    .getConfiguration().getGroup());
            // 新增节点
            for (InetSocketAddress address : addressList) {
                if (!RpcBootstrap.CHANNEL_MAP.containsKey(address)) {
                    log.info("新增服务节点[{}:{}]，尝试连接上线", address.getHostString(), address.getPort());
                    try {
                        // 使用Netty的Bootstrap发起异步连接，同步阻塞直到连接建立成功
                        Channel channel = NettyBootstrapInitializer.getBootstrap()
                                .connect(address)  // 连接到指定服务节点地址（IP+端口）
                                .sync()   // 等待连接完成
                                .channel();  // 获取建立成功的 Channel（通道）
                        // 缓存channel
                        RpcBootstrap.CHANNEL_MAP.put(address, channel);
                        log.info("成功连接到服务节点[{}:{}]，服务已成功上线", address.getHostString(), address.getPort());
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            // 移除已下线的节点
            RpcBootstrap.CHANNEL_MAP.keySet().removeIf(existingAddress -> {
                // 如果当前缓存中的地址不在最新的服务列表中，说明该节点已下线
                if (!addressList.contains(existingAddress)) {
                    // 获取对应的Channel连接
                    Channel channel = RpcBootstrap.CHANNEL_MAP.get(existingAddress);
                    // 如果连接存在且仍然处于活跃状态，则关闭连接释放资源
                    if (channel != null && channel.isActive()) {
                        channel.close();
                    }
                    log.info("服务节点[{}:{}]已下线，移除缓存并关闭连接", existingAddress.getHostString(),
                            existingAddress.getPort());
                    return true;
                }
                return false;
            });
            // 重新负载均衡
            RpcBootstrap.getInstance().getConfiguration().getLoadBalancer().reLoadBalance(serviceName, addressList);
            log.info("刷新负载均衡器，服务[{}]当前列表为：{}", serviceName, addressList);
        }
    }
}
