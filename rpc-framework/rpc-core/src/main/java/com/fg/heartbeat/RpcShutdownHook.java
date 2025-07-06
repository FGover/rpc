package com.fg.heartbeat;


public class RpcShutdownHook extends Thread {
    @Override
    public void run() {
        // 打开挡板，阻止新请求（除了心跳）
        ShutdownHolder.enableBaffle();
        // 记录开始时间
        long start = System.currentTimeMillis();
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // 请求全部处理完 || 等待超过 5 秒 => 退出
        } while (ShutdownHolder.hasProcessingRequests() && System.currentTimeMillis() - start <= 5000);
    }
}
