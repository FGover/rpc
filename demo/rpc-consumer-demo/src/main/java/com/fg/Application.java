package com.fg;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
public class Application {
    public static void main(String[] args) {

        // 设置系统属性
        System.setProperty("rpc.mode", "consumer");

        // 配置要引用的远程服务接口
        ReferenceConfig<HelloRpcService> reference = new ReferenceConfig<>();
        reference.setInterface(HelloRpcService.class);

        // 启动并初始化
        RpcBootstrap.getInstance()
                .group("main")
                .reference(reference);

        // 获取远程服务代理对象
        HelloRpcService helloRpc = reference.get();
        // 调用远程服务
//        int threadCount = 3;
//        ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadCount);
//        for (int i = 0; i < threadCount; i++) {
//            int threadId = i;
//            executor.scheduleAtFixedRate(() -> {
//                try {
//                    // 模拟传递参数：区分调用来源
//                    String msg = helloRpc.sayHello("线程 " + threadId);
//                    log.info("调用结果：{}", msg);
//                } catch (Exception e) {
//                    log.error("线程 {} 调用异常：{}", threadId, e.getMessage());
//                }
//            }, 0, 100, TimeUnit.MILLISECONDS);
//        }
        String result = helloRpc.sayHello("test");
        System.out.println("第一次调用结果: " + result);

        System.out.println("=== 开始测试幂等性 ===");
        String r = helloRpc.getIdempotentTest("test");
        System.out.println("结果: " + r);
    }
}
