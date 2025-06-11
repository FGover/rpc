package com.fg;

import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class RpcBootstrap {

    // 饿汉式单例
    private static final RpcBootstrap rpcBootstrap = new RpcBootstrap();

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
     * @param appName
     * @return
     */
    public RpcBootstrap application(String appName) {
        return this;
    }

    /**
     * 配置注册中心
     *
     * @param registry
     * @return
     */
    public RpcBootstrap registry(RegistryConfig registry) {
        return this;
    }

    /**
     * 配置通信协议
     *
     * @param protocol
     * @return
     */
    public RpcBootstrap protocol(ProtocolConfig protocol) {
        if (log.isDebugEnabled()) {
            log.debug("配置通信协议:{}", protocol);
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
        if (log.isDebugEnabled()) {
            log.debug("发布服务:{}", service);
        }
        return this;
    }

    /**
     * 发布多个服务
     *
     * @param services
     * @return
     */
    public RpcBootstrap publish(List<?> services) {
        return this;
    }

    /**
     * 启动服务
     */
    public void start() {
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
