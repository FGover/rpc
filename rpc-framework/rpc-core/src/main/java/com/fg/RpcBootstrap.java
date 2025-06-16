package com.fg;

import com.fg.channel.handler.RpcMessageDecoder;
import com.fg.discovery.Registry;
import com.fg.discovery.RegistryConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


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
    // 服务列表
    private static final Map<String, ServiceConfig<?>> SERVICE_LIST = new ConcurrentHashMap<>(16);
    // 连接缓存
    public static final Map<InetSocketAddress, Channel> CHANNEL_MAP = new ConcurrentHashMap<>(16);
    // 定义全局的对外挂起的completableFuture
    public static final Map<Long, CompletableFuture<Object>> PENDING_REQUEST_MAP = new ConcurrentHashMap<>(128);

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
        SERVICE_LIST.put(service.getInterface().getName(), service);
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
     * 启动Netty服务
     */
    public void start() {
        // bossGroup 负责接收客户端连接，一个线程即可
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // workerGroup 负责处理客户端I/O事件，默认线程数为 CPU*2
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 创建Netty服务启动类
            ServerBootstrap bootstrap = new ServerBootstrap();
            // 配置启动参数
            bootstrap.group(bossGroup, workerGroup)  // 设置线程组
                    .channel(NioServerSocketChannel.class)  // 设置通道类型
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new LoggingHandler())
                                    .addLast(new RpcMessageDecoder());
                        }
                    });  // 设置通道初始化器
            // 绑定端口并同步阻塞知道绑定完成
            ChannelFuture channelFuture = bootstrap.bind(port).sync();
            log.info("Netty服务已启动，监听端口：{}", port);
            // 阻塞直到服务通道关闭
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 关闭线程组
            try {
                bossGroup.shutdownGracefully().sync();
                workerGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 用于配置 consumer 引用的远程服务接口
     *
     * @param reference
     * @return
     */
    public RpcBootstrap reference(ReferenceConfig<?> reference) {
        // 设置consumer引用的远程服务接口的注册中心配置
        reference.setRegistry(registry);
        return this;
    }
}
