package com.fg.discovery.Impl;

import com.fg.Constant;
import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.discovery.AbstractRegistry;
import com.fg.exception.DiscoveryException;
import com.fg.utils.NetUtil;
import com.fg.utils.zookeeper.ZookeeperNode;
import com.fg.utils.zookeeper.ZookeeperUtil;
import com.fg.watcher.ServiceChangeWatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
public class ZookeeperRegistry extends AbstractRegistry {

    ZooKeeper zooKeeper;

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
        // 服务名称的节点
        String parentNode = Constant.BASE_PROVIDERS_PATH + "/" + service.getInterface().getName() + "/" + service.getGroup();
        // 创建节点
        if (!ZookeeperUtil.exists(zooKeeper, parentNode, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(parentNode, null), null,
                    CreateMode.PERSISTENT);
        }
        // 创建本机的临时节点
        String node = parentNode + "/" + NetUtil.getIP() + ":" + RpcBootstrap.getInstance().getConfiguration().getPort();
        if (!ZookeeperUtil.exists(zooKeeper, node, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(node, null), null,
                    CreateMode.EPHEMERAL);
        }
        if (log.isDebugEnabled()) {
            log.debug("发布服务:{}, group={}", service, service.getGroup());
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
        // 找到服务对应的节点
        String serviceNode = Constant.BASE_PROVIDERS_PATH + "/" + serviceName + "/" + group;
        // 获取子节点
        List<String> children = ZookeeperUtil.getChildren(zooKeeper, serviceNode, new ServiceChangeWatcher(serviceName));
        // 获取子节点的ip和端口
        List<InetSocketAddress> inetSocketAddressList = children.stream().map(child -> {
            String[] info = child.split(":");
            return new InetSocketAddress(info[0], Integer.parseInt(info[1]));
        }).toList();
        if (inetSocketAddressList.isEmpty()) {
            throw new DiscoveryException("未找到服务");
        }
        return inetSocketAddressList;
    }
}
