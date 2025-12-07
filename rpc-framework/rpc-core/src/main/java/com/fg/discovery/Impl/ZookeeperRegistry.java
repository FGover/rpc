package com.fg.discovery.Impl;

import com.fg.Constant;
import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.discovery.AbstractRegistry;
import com.fg.exception.DiscoveryException;
import com.fg.utils.NetUtil;
import com.fg.utils.zookeeper.ZookeeperNode;
import com.fg.utils.zookeeper.ZookeeperUtil;
import com.fg.watcher.ZkServiceChangeWatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ZookeeperRegistry extends AbstractRegistry {

    private ZooKeeper zooKeeper;

    // 防重复订阅（服务名::组名）
    private final ConcurrentHashMap<String, Boolean> subscribed = new ConcurrentHashMap<>();
    // 监听器缓存
    private final ConcurrentHashMap<String, ZkServiceChangeWatcher> watchers = new ConcurrentHashMap<>();

    public ZookeeperRegistry() {
        this.zooKeeper = ZookeeperUtil.createZookeeper();
    }

    public ZookeeperRegistry(String connectString, int timeout) {
        this.zooKeeper = ZookeeperUtil.createZookeeper(connectString, timeout);
    }

    /**
     * 注册服务
     *
     * @param service
     */
    @Override
    public void register(ServiceConfig<?> service) {
        String serviceName = service.getInterface().getName();
        String group = service.getGroup() != null ? service.getGroup() : "default";
        String ip = NetUtil.getIP();
        int port = RpcBootstrap.getInstance().getConfiguration().getPort();
        // 服务名称的节点
        String parentNode = Constant.BASE_PROVIDERS_PATH + "/" + serviceName + "/" + group;
        // 创建节点
        if (!ZookeeperUtil.exists(zooKeeper, parentNode, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(parentNode, null), null,
                    CreateMode.PERSISTENT);
        }
        // 创建本机的临时节点（实例）
        String node = parentNode + "/" + ip + ":" + port;
        if (!ZookeeperUtil.exists(zooKeeper, node, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(node, null), null,
                    CreateMode.EPHEMERAL);
            log.info("服务已注册到Zookeeper: {} (group={}, node={})", serviceName, group, node);
        } else {
            log.debug("服务节点已存在: {}", node);
        }
    }

    /**
     * 注销服务
     *
     * @param serviceName
     * @param address
     */
    @Override
    public void unregister(String serviceName, String group, InetSocketAddress address) {
        String node = Constant.BASE_PROVIDERS_PATH + "/" + serviceName + "/" + group + "/" +
                address.getHostString() + ":" + address.getPort();
        try {
            ZookeeperUtil.deleteNode(zooKeeper, node);
            log.info("服务已从Zookeeper注销：{}（group={}, node={}）", serviceName, group, node);
        } catch (Exception e) {
            log.warn("注销服务失败：{}（group={}, node={}）", serviceName, group, node);
        }
    }

    /**
     * 查找服务
     *
     * @param serviceName
     * @return 服务列表
     */
    @Override
    public List<InetSocketAddress> lookup(String serviceName, String group) {
        String targetGroup = group != null ? group : "default";
        String subKey = serviceName + "::" + targetGroup;
        // 找到服务对应的节点
        String serviceNode = Constant.BASE_PROVIDERS_PATH + "/" + serviceName + "/" + targetGroup;
        // 防重复订阅：如果已订阅过，直接获取一次最新列表
        if (subscribed.putIfAbsent(subKey, Boolean.TRUE) == null) {
            // 首次订阅，创建Watcher缓存
            ZkServiceChangeWatcher watcher = new ZkServiceChangeWatcher(serviceName, targetGroup);
            watchers.put(subKey, watcher);
            // 获取子节点
            List<String> children = ZookeeperUtil.getChildren(zooKeeper, serviceNode, watcher);
            // 获取子节点的ip和端口
            List<InetSocketAddress> inetSocketAddressList = children.stream().map(child -> {
                String[] info = child.split(":");
                return new InetSocketAddress(info[0], Integer.parseInt(info[1]));
            }).toList();
            if (inetSocketAddressList.isEmpty()) {
                throw new DiscoveryException("未找到服务: " + serviceName + " (group=" + targetGroup + ")");
            }
            log.info("首次订阅服务: {} (group={}), 发现{}个实例", serviceName, targetGroup, inetSocketAddressList.size());
            return inetSocketAddressList;
        }
        // 已订阅过，但ZK的Watcher是一次性的，每次getChildren都需要重新设置Watcher才能继续监听
        ZkServiceChangeWatcher watcher = watchers.get(subKey);
        // 重新设置Watcher继续监听
        List<String> children = ZookeeperUtil.getChildren(zooKeeper, serviceNode, watcher);
        List<InetSocketAddress> inetSocketAddressList = children.stream().map(child -> {
            String[] info = child.split(":");
            return new InetSocketAddress(info[0], Integer.parseInt(info[1]));
        }).toList();
        if (inetSocketAddressList.isEmpty()) {
            throw new DiscoveryException("未找到服务: " + serviceName + " (group=" + targetGroup + ")");
        }
        return inetSocketAddressList;
    }

    public void shutdown() {
        try {
            // 清理订阅标记和Watcher缓存
            subscribed.clear();
            watchers.clear();
            // 关闭ZooKeeper连接
            if (zooKeeper != null) {
                ZookeeperUtil.close(zooKeeper);
                log.info("Zookeeper连接已关闭");
            }
        } catch (Exception e) {
            log.warn("关闭Zookeeper连接失败: {}", e.getMessage());
        }
    }
}
