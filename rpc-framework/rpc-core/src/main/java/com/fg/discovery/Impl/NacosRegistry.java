package com.fg.discovery.Impl;

import com.fg.Constant;
import com.fg.ServiceConfig;
import com.fg.discovery.AbstractRegistry;
import com.fg.utils.NetUtil;
import com.fg.utils.zookeeper.ZookeeperNode;
import com.fg.utils.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;

import java.net.InetSocketAddress;

@Slf4j
public class NacosRegistry extends AbstractRegistry {

    ZooKeeper zooKeeper;

    public NacosRegistry() {
        this.zooKeeper = ZookeeperUtil.createZookeeper();
    }

    public NacosRegistry(String connectString, int timeout) {
        this.zooKeeper = ZookeeperUtil.createZookeeper(connectString, timeout);
    }

    @Override
    public void register(ServiceConfig<?> service) {
        // 服务名称的节点
        String parentNode = Constant.BASE_PROVIDERS_PATH + "/" + service.getInterface().getName();
        // 创建节点
        if (!ZookeeperUtil.exists(zooKeeper, parentNode, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(parentNode, null), null,
                    CreateMode.PERSISTENT);
        }
        // 创建本机的临时节点
        String node = parentNode + "/" + NetUtil.getIP() + ":" + 8088;
        if (!ZookeeperUtil.exists(zooKeeper, node, null)) {
            ZookeeperUtil.createNode(zooKeeper, new ZookeeperNode(node, null), null,
                    CreateMode.EPHEMERAL);
        }
        if (log.isDebugEnabled()) {
            log.debug("发布服务:{}", service);
        }
    }

    @Override
    public InetSocketAddress lookup(String name) {
        return null;
    }
}
