package com.fg.zookeeper;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class MyWatcher implements Watcher {

    @Override
    public void process(WatchedEvent event) {
        // 判断事件类型，连接类型的事件
        // None表示会话事件
        if (event.getType() == Event.EventType.None) {
            if (event.getState() == Event.KeeperState.SyncConnected) {
                System.out.println("zookeeper连接成功");
            } else if (event.getState() == Event.KeeperState.AuthFailed) {
                System.out.println("zookeeper认证失败");
            } else if (event.getState() == Event.KeeperState.Disconnected) {
                System.out.println("zookeeper连接断开");
            }
        } else if (event.getType() == Event.EventType.NodeCreated) {
            System.out.println(event.getPath() + " 节点创建");
        } else if (event.getType() == Event.EventType.NodeDeleted) {
            System.out.println(event.getPath() + " 节点删除");
        } else if (event.getType() == Event.EventType.NodeDataChanged) {
            System.out.println(event.getPath() + " 节点数据改变");
        } else if (event.getType() == Event.EventType.NodeChildrenChanged) {
            System.out.println(event.getPath() + " 子节点改变");
        }
    }
}
