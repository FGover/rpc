package com.fg;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
public class Application {
    public static void main(String[] args) {
        // 配置要引用的远程服务接口
        ReferenceConfig<HelloRpcService> reference = new ReferenceConfig<>();
        reference.setInterface(HelloRpcService.class);

        // 启动并初始化
        RpcBootstrap.getInstance()
//                .application("first-rpc-consumer")
//                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                .group("main")
                .reference(reference);

        // 获取远程服务代理对象
        HelloRpcService helloRpc = reference.get();
        // 调用远程服务
//        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
//        executor.scheduleAtFixedRate(() -> {
//            try {
//                String msg = helloRpc.sayHello("你好，定时调用");
//                log.info("远程调用结果：{}", msg);
//            } catch (Exception e) {
//                log.error("远程调用异常", e);
//            }
//        }, 0, 2, TimeUnit.SECONDS);
        int threadCount = 10;
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.scheduleAtFixedRate(() -> {
                try {
                    // 模拟传递参数：区分调用来源
                    String msg = helloRpc.sayHello("线程 " + threadId);
                    log.info("调用结果：{}", msg);
                } catch (Exception e) {
                    log.error("线程 {} 调用异常：{}", threadId, e.getMessage());
                }
            }, 0, 300, TimeUnit.MILLISECONDS); // 每个线程每 300ms 发一次请求
        }
    }
}
