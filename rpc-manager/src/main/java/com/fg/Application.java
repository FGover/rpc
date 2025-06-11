package com.fg;

import com.fg.exception.ZookeeperException;
import com.fg.utils.zookeeper.ZookeeperNode;
import com.fg.utils.zookeeper.ZookeeperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

/**
 * 注册中心的管理页面
 */
@Slf4j
public class Application {
    public static void main(String[] args) {
        ZooKeeper zooKeeper = ZookeeperUtil.createZookeeper();

        // 定义节点和数据
        String basePath = "/rpc-metadata";
        String providerPath = basePath + "/provider";
        String consumerPath = basePath + "/consumer";

        // 创建节点
        ZookeeperNode baseNode = new ZookeeperNode(basePath, null);
        ZookeeperNode providerNode = new ZookeeperNode(providerPath, null);
        ZookeeperNode consumerNode = new ZookeeperNode(consumerPath, null);

        List.of(baseNode, providerNode, consumerNode).forEach(node -> {
            ZookeeperUtil.createNode(zooKeeper, node, null, CreateMode.PERSISTENT);
        });

        // 关闭连接
        ZookeeperUtil.close(zooKeeper);
    }
}
