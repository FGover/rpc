package com.fg;

import com.fg.discovery.Registry;
import com.fg.discovery.RegistryConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class RpcBootstrap {

    // 饿汉式单例
    private static final RpcBootstrap rpcBootstrap = new RpcBootstrap();

    // 定义基础配置
    private String applicationName = "default";
    private RegistryConfig registryConfig;
    private ProtocolConfig protocolConfig;
    private int port = 8088;

    private Registry registry;

    // 私有化构造方法，防止外部实例化
    private RpcBootstrap() {
    }

    // 获取实例的静态方法
    public static RpcBootstrap getInstance() {
        return rpcBootstrap;
    }

    /**
     * 设置应用信息
     *
     * @param applicationName
     * @return
     */
    public RpcBootstrap application(String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    /**
     * 配置注册中心
     *
     * @param registryConfig
     * @return
     */
    public RpcBootstrap registry(RegistryConfig registryConfig) {
        this.registry = registryConfig.getRegistry();
        return this;
    }

    /**
     * 配置通信协议
     *
     * @param protocolConfig
     * @return
     */
    public RpcBootstrap protocol(ProtocolConfig protocolConfig) {
        this.protocolConfig = protocolConfig;
        if (log.isDebugEnabled()) {
            log.debug("配置通信协议:{}", protocolConfig);
        }
        return this;
    }

    /**
     * 发布服务
     *
     * @param service
     * @return
     */
    public RpcBootstrap publish(ServiceConfig<?> service) {
        // 封装要发布的服务
        registry.register(service);
        return this;
    }

    /**
     * 发布多个服务
     *
     * @param services
     * @return
     */
    public RpcBootstrap publish(List<ServiceConfig<?>> services) {
        for (ServiceConfig<?> service : services) {
            this.publish(service);
        }
        return this;
    }

    /**
     * 启动服务
     */
    public void start() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 用于配置 consumer 引用的远程服务接口
     *
     * @param reference
     * @return
     */
    public RpcBootstrap reference(ReferenceConfig<?> reference) {
        return this;
    }
}
