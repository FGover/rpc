package com.fg;

import com.fg.discovery.RegistryConfig;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Application {
    public static void main(String[] args) {
        // 配置要引用的远程服务接口
        ReferenceConfig<HelloRpcService> reference = new ReferenceConfig<>();
        reference.setInterface(HelloRpcService.class);

        // 启动并初始化
        RpcBootstrap.getInstance()
                .application("first-rpc-consumer")
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                .serializer("hessian")
                .compress("zstd")
                .reference(reference);

        // 获取远程服务代理对象
        HelloRpcService helloRpc = reference.get();
//        String msg = helloRpc.sayHello("你好");
//        log.info("远程调用结果：{}", msg);
        for (int i = 0; i < 5; i++) {
            String msg = helloRpc.sayHello("你好，第 " + (i + 1) + " 次调用");
            log.info("远程调用结果：{}", msg);

            // 可以稍微等待，避免调用太快
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
