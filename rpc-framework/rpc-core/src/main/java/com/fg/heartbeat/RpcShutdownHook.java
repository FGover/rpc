package com.fg.heartbeat;


import com.fg.RpcBootstrap;
import com.fg.discovery.Impl.NacosRegistry;
import com.fg.discovery.Impl.ZookeeperRegistry;
import com.fg.discovery.Registry;

public class RpcShutdownHook extends Thread {
    @Override
    public void run() {
        // 打开挡板，阻止新请求（除了心跳）
        ShutdownHolder.enableBaffle();
        HeartBeatDetector.stop();  // 先停止心跳定时器，避免关停期间产生心跳
        // 记录开始时间
        long start = System.currentTimeMillis();
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            // 请求全部处理完 || 等待超过 5 秒 => 退出
        } while (ShutdownHolder.hasProcessingRequests() && System.currentTimeMillis() - start <= 5000);
        // 取消nacos订阅并关闭客户端（存在才执行）
        try {
            Registry registry = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig().getRegistry();
            if (registry instanceof NacosRegistry nacosRegistry) {
                nacosRegistry.shutdown();
            } else if (registry instanceof ZookeeperRegistry zkRegistry) {
                zkRegistry.shutdown();
            }
        } catch (Exception ignore) {
        }
    }
}
