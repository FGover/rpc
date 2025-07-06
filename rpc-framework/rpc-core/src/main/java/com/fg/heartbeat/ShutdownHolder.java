package com.fg.heartbeat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public class ShutdownHolder {

    // 服务关闭挡板：为 true 表示拒绝所有请求（只允许心跳）
    public static AtomicBoolean BAFFLE = new AtomicBoolean(false);
    // 正在处理的请求数量（用于优雅停机时等待请求完成）
    public static LongAdder REQUEST_COUNT = new LongAdder();

    /**
     * 开启挡板（进入关闭模式）
     */
    public static void enableBaffle() {
        BAFFLE.set(true);
    }

    /**
     * 关闭挡板（恢复正常服务）
     */
    public static void disableBaffle() {
        BAFFLE.set(false);
    }

    /**
     * 判断当前是否还有正在处理的请求
     *
     * @return
     */
    public static boolean hasProcessingRequests() {
        return REQUEST_COUNT.sum() > 0;
    }
}
