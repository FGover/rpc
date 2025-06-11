package com.fg.utils.zookeeper;

import com.fg.Constant;
import com.fg.exception.ZookeeperException;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;

import java.util.concurrent.CountDownLatch;

@Slf4j
public class ZookeeperUtil {

    // 默认连接
    public static ZooKeeper createZookeeper() {
        String connectString = Constant.DEFAULT_ZK_CONNECT;
        int timeout = Constant.TIME_OUT;
        return createZookeeper(connectString, timeout);
    }

    /**
     * 创建zookeeper实例
     *
     * @param connectString
     * @param timeout
     * @return
     */
    public static ZooKeeper createZookeeper(String connectString, int timeout) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            final ZooKeeper zooKeeper = new ZooKeeper(connectString, timeout, event -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    System.out.println("连接成功");
                    countDownLatch.countDown();
                }
            });
            countDownLatch.await();
            return zooKeeper;
        } catch (Exception e) {
            log.error("创建Zookeeper实例失败", e);
            throw new ZookeeperException();
        }
    }

    /**
     * 创建节点
     *
     * @param zooKeeper
     * @param node
     * @param watcher
     * @param createMode
     */
    public static void createNode(ZooKeeper zooKeeper, ZookeeperNode node, Watcher watcher,
                                  CreateMode createMode) {
        // 判断节点是否存在
        try {
            if (zooKeeper.exists(node.getNodePath(), watcher) == null) {
                String result = zooKeeper.create(node.getNodePath(), node.getData(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        createMode);
                log.info("创建节点成功:{}", result);
            } else {
                if (log.isDebugEnabled()) {
                    log.info("节点已存在:{}", node.getNodePath());
                }
            }
        } catch (KeeperException | InterruptedException e) {
            log.error("创建节点失败", e);
            throw new ZookeeperException();
        }
    }

    /**
     * 关闭Zookeeper实例
     *
     * @param zooKeeper
     */
    public static void close(ZooKeeper zooKeeper) {
        try {
            zooKeeper.close();
        } catch (InterruptedException e) {
            log.error("关闭Zookeeper实例失败", e);
            throw new ZookeeperException();
        }
    }
}
