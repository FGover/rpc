package com.fg.proxy.handler;

import com.fg.Constant;
import com.fg.NettyBootstrapInitializer;
import com.fg.RpcBootstrap;
import com.fg.compress.CompressFactory;
import com.fg.discovery.Registry;
import com.fg.enums.RequestType;
import com.fg.exception.DiscoveryException;
import com.fg.serialize.SerializerFactory;
import com.fg.transport.message.RequestPayload;
import com.fg.transport.message.RpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 每个代理对象远程调用过程都封装在了 invoke 方法中
 * 发现可用服务 -> 建立连接 -> 发送请求 -> 得到结果
 */
@Slf4j
public class RpcConsumerInvocationHandler implements InvocationHandler {

    // 注册中心
    private final Registry registry;
    // 接口引用（通过接口引用，服务消费者可以调用服务提供者实现的具体方法）
    private final Class<?> interfaceRef;

    public RpcConsumerInvocationHandler(Registry registry, Class<?> interfaceRef) {
        this.registry = registry;
        this.interfaceRef = interfaceRef;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.info("调用远程方法：{}", method.getName());
        // 1.获取可用服务
        InetSocketAddress address = RpcBootstrap.LOAD_BALANCER.getServiceAddress(interfaceRef.getName());
        log.info("找到{}服务，地址：{}:{}", interfaceRef.getName(), address.getHostString(), address.getPort());
        // 2.通过 Netty 客户端发送请求，从全局缓存中获取一个通道
        Channel channel = getAvailableChannel(address);
        // 3.封装请求报文
        RequestPayload requestPayload = RequestPayload.builder()
                .interfaceName(interfaceRef.getName())
                .methodName(method.getName())
                .parametersType(method.getParameterTypes())
                .parametersValue(args)
                .returnType(method.getReturnType())
                .build();
        RpcRequest request = RpcRequest.builder()
                .requestId(RpcBootstrap.ID_GENERATOR.nextId())
                .requestType(RequestType.REQUEST.getId())
                .compressType(CompressFactory.getCompressor(RpcBootstrap.COMPRESSOR_TYPE).getCode())
                .serializeType(SerializerFactory.getSerializer(RpcBootstrap.SERIALIZER_TYPE).getCode())
                .requestPayload(requestPayload)
                .build();
        // 4.发送请求
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        RpcBootstrap.PENDING_REQUEST_MAP.put(request.getRequestId(), completableFuture);
        channel.writeAndFlush(request)
                .addListener((ChannelFutureListener) promise -> {
                    if (!promise.isSuccess()) {
                        completableFuture.completeExceptionally(promise.cause());
                    }
                });
        // 5.阻塞等待响应结果
        return completableFuture.get(10, TimeUnit.SECONDS);
    }

    /**
     * 从缓存中获取一个可用的通道，如果缓存中没有，则创建一个通道，尝试连接服务器
     *
     * @param address
     * @return
     */
    private Channel getAvailableChannel(InetSocketAddress address) {
        // 从缓存中获取通道
        Channel channel = RpcBootstrap.CHANNEL_MAP.get(address);
        if (channel == null) {
            // 如果缓存中没有，则创建一个通道，尝试连接服务器
            // sync()同步等待连接完成，如果发生异常会抛出
            // await()阻塞当前线程等待某个操作完成，如果发生异常不会抛出，只会返回false（需手动检查）
            // channel = NettyBootstrapInitializer.getBootstrap().connect(address).await().channel();
            // 异步实现
            CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
            NettyBootstrapInitializer.getBootstrap().connect(address).addListener(
                    (ChannelFutureListener) promise -> {
                        if (promise.isSuccess()) {
                            if (log.isDebugEnabled()) {
                                log.debug("连接服务器成功，地址：{}:{}", address.getHostString(), address.getPort());
                            }
                            channelFuture.complete(promise.channel());
                        } else {
                            log.error("连接服务器失败，地址：{}:{}",
                                    address.getHostString(), address.getPort(), promise.cause());
                            channelFuture.completeExceptionally(promise.cause());
                        }
                    });
            // 阻塞获取channel
            try {
                channel = channelFuture.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("获取通道时发生异常：", e);
                throw new DiscoveryException(e);
            }
            // 缓存channel
            RpcBootstrap.CHANNEL_MAP.put(address, channel);
        }
        return channel;
    }
}
