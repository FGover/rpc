package com.fg;

import com.fg.Impl.HelloRpcServiceImpl;
import com.fg.discovery.RegistryConfig;

public class Application {
    public static void main(String[] args) {

        // 封装要发布的服务
        ServiceConfig<HelloRpcService> service = new ServiceConfig<>();
        service.setInterface(HelloRpcService.class);
        service.setRef(new HelloRpcServiceImpl());

        // 服务提供方，需要注册服务，启动类
        RpcBootstrap.getInstance()  // 获取启动类实例
//                .application("first-rpc-provider")  // 设置应用信息
//                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))   // 连接注册中心
//                .serializer("jdk")
//                .compress("gzip")
                .scan("com.fg")   // 扫描指定包下的服务
                .start();   // 启动服务
    }
}
