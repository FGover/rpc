package com.fg.utils.zookeeper;

import com.fg.Constant;
import com.fg.exception.ZookeeperException;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;

import java.util.List;
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
                    log.info("客户端{}连接成功", event.getPath());
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
     * 判断节点是否存在
     *
     * @param zooKeeper
     * @param node
     * @param watcher
     * @return
     */
    public static boolean exists(ZooKeeper zooKeeper, String node, Watcher watcher) {
        try {
            return zooKeeper.exists(node, watcher) != null;
        } catch (KeeperException | InterruptedException e) {
            log.error("判断节点[{}]是否存在失败", node, e);
            throw new ZookeeperException(e);
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

    /**
     * 获取子节点
     *
     * @param zooKeeper
     * @param serviceNode
     * @return
     */
    public static List<String> getChildren(ZooKeeper zooKeeper, String serviceNode, Watcher watcher) {
        try {
            return zooKeeper.getChildren(serviceNode, watcher);
        } catch (InterruptedException | KeeperException e) {
            log.error("获取子节点失败", e);
            throw new ZookeeperException(e);
        }
    }
}
