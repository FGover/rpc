package com.fg.heartbeat;

import com.fg.NettyBootstrapInitializer;
import com.fg.RpcBootstrap;
import com.fg.compressor.CompressorFactory;
import com.fg.discovery.Registry;
import com.fg.enums.RequestType;
import com.fg.serializer.SerializerFactory;
import com.fg.transport.message.RpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class HeartBeatDetector {

    private static volatile ScheduledExecutorService heartbeatExecutor;
    private static volatile boolean isRunning = false;
    private static String serviceName;

    /**
     * 心跳检测
     * 1.从注册中心拉取指定服务名称的所有节点地址
     * 2.针对每个节点地址，建立Netty连接并缓存
     * 3.启动定时任务定期发送心跳包探测存活状态
     *
     * @param serviceName
     */
    public static void detect(String serviceName) {
        HeartBeatDetector.serviceName = serviceName;
        log.info("开始服务[{}]的心跳检测", serviceName);
        // 获取注册中心实例
        Registry registry = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig().getRegistry();
        // 拉取服务节点列表
        List<InetSocketAddress> serviceList = registry.lookup(serviceName, RpcBootstrap.getInstance().getConfiguration()
                .getGroup());
        // 建立连接缓存
        for (InetSocketAddress address : serviceList) {
            try {
                // 如果缓存中没有对应节点连接，则新建连接并缓存
                if (!RpcBootstrap.CHANNEL_MAP.containsKey(address)) {
                    Channel channel = NettyBootstrapInitializer.getBootstrap()
                            .connect(address)
                            .sync()
                            .channel();
                    RpcBootstrap.CHANNEL_MAP.put(address, channel);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("建立连接失败", e);
                throw new RuntimeException(e);
            }
        }
        if (isRunning) {
            log.debug("心跳检测已在运行中");
            return;
        }
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "rpc-HeartBeatDetector-exec");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(new HeartBeatTask(), 0, 3, TimeUnit.SECONDS);
        isRunning = true;
    }

    public static void stop() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        isRunning = false;
    }

    /**
     * 定时任务，周期性发送心跳包检测每个服务节点状态
     */
    private static class HeartBeatTask extends TimerTask {

        @Override
        public void run() {
            // 清空本轮的响应时间缓存
            RpcBootstrap.RESPONSE_TIME_CHANNEL_MAP.clear();
            // 遍历缓存的所有节点channel
            Map<InetSocketAddress, Channel> channelCache = RpcBootstrap.CHANNEL_MAP;
            for (Map.Entry<InetSocketAddress, Channel> entry : channelCache.entrySet()) {
                log.debug("开始检测服务节点: {}", entry.getKey());
                int tryTimes = 3;   // 重试次数
                while (tryTimes > 0) {
                    Channel channel = entry.getValue();
                    long startTime = System.currentTimeMillis();  // 记录发送时间
                    // 构建心跳请求对象
                    RpcRequest request = RpcRequest.builder()
                            .requestId(RpcBootstrap.getInstance().getConfiguration().getIdGenerator().getId())
                            .compressType(CompressorFactory.getCompressor(RpcBootstrap.getInstance().getConfiguration()
                                    .getCompressType()).getCode())
                            .serializeType(SerializerFactory.getSerializer(RpcBootstrap.getInstance().getConfiguration()
                                    .getSerializeType()).getCode())
                            .requestType(RequestType.HEARTBEAT.getId())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    // 用CompletableFuture异步等待响应结果
                    CompletableFuture<Object> completableFuture = new CompletableFuture<>();
                    // 将Future存入待响应请求集合，供响应时回调处理完成
                    RpcBootstrap.PENDING_REQUEST_MAP.put(request.getRequestId(), completableFuture);
                    // 发送心跳请求消息
                    channel.writeAndFlush(request).addListener((ChannelFutureListener) promise -> {
                        if (!promise.isSuccess()) {
                            // 发送失败，CompletableFuture异常完成
                            completableFuture.completeExceptionally(promise.cause());
                        }
                    });
                    long endTime = 0L;  // 记录响应时间
                    try {
                        // 阻塞等待响应结果，超时设置为1秒，防止无限阻塞
                        completableFuture.get(1, TimeUnit.SECONDS);
                        endTime = System.currentTimeMillis();
                    } catch (ExecutionException | InterruptedException | TimeoutException e) {
                        tryTimes--;
                        log.warn("心跳检测: [{}]服务节点异常, 第{}次重试中...", entry.getKey(), 3 - tryTimes);
                        if (tryTimes == 0) {
                            // 重试次数用尽，关闭连接并从缓存中移除
                            log.error("节点[{}]心跳连接失败，移除缓存并关闭连接", entry.getKey());
                            RpcBootstrap.CHANNEL_MAP.remove(entry.getKey());
                            RpcBootstrap.PENDING_REQUEST_MAP.remove(request.getRequestId());
                            RpcBootstrap.RESPONSE_TIME_CHANNEL_MAP.entrySet()
                                    .removeIf(item -> item.getValue().equals(channel));
                            // 从注册中心下线
                            Registry registry = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig()
                                    .getRegistry();
                            registry.unregister(serviceName, entry.getKey());
                            // 关闭channel
                            if (channel.isOpen()) {
                                channel.close();
                            }
                        }
                        // 重试前随机等待一小段时间，避免雪崩
                        try {
                            // 指数退避
                            int retryDelay = 1000 * (1 << (3 - tryTimes));   // 1000ms, 2000ms, 4000ms
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            log.warn("心跳检测被中断");
                            return;
                        }
                        continue;
                    }
                    // 计算响应时间
                    long time = endTime - startTime;
                    log.debug("服务[{}]响应时间：{}", entry.getKey(), time);
                    // 缓存响应时间与对应Channel，方便做负载均衡时优先选择响应快的节点
                    RpcBootstrap.RESPONSE_TIME_CHANNEL_MAP.put(time, channel);
                    break;  // 心跳检测成功，跳出重试循环
                }
            }
        }
    }
}
