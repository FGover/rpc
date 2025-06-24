package com.fg.heartbeat;

import com.fg.NettyBootstrapInitializer;
import com.fg.RpcBootstrap;
import com.fg.compress.CompressFactory;
import com.fg.discovery.Registry;
import com.fg.enums.RequestType;
import com.fg.serialize.SerializerFactory;
import com.fg.transport.message.RpcRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class HeartBeatDetector {

    /**
     * 心跳检测
     * 1.从注册中心拉取指定服务名称的所有节点地址
     * 2.针对每个节点地址，建立Netty连接并缓存
     * 3.启动定时任务定期发送心跳包探测存活状态
     *
     * @param serviceName
     */
    public static void detect(String serviceName) {
        log.info("开始服务[{}]的心跳检测", serviceName);
        // 获取注册中心实例
        Registry registry = RpcBootstrap.getInstance().getConfiguration().getRegistryConfig().getRegistry();
        // 拉取服务节点列表
        List<InetSocketAddress> serviceList = registry.lookup(serviceName);
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
                log.error("建立连接失败", e);
                throw new RuntimeException(e);
            }
        }
        // 启动定时任务检测线程（守护线程）
        Thread thread = new Thread(() -> new Timer().scheduleAtFixedRate(new HeartBeatTask(), 0, 2000),
                "rpc-HeartBeatDetector-thread");
        thread.setDaemon(true);
        thread.start();
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
                log.info("开始检测服务节点: {}", entry.getKey());
                int tryTimes = 3;   // 重试次数
                while (tryTimes > 0) {
                    Channel channel = entry.getValue();
                    long startTime = System.currentTimeMillis();  // 记录发送时间
                    // 构建心跳请求对象
                    RpcRequest request = RpcRequest.builder()
                            .requestId(RpcBootstrap.getInstance().getConfiguration().getIdGenerator().getId())
                            .compressType(CompressFactory.getCompressor(RpcBootstrap.getInstance().getConfiguration()
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
                        log.warn("心跳检测: [{}]服务节点异常, 第{}次重试中...", channel.remoteAddress(), 3 - tryTimes);
                        if (tryTimes == 0) {
                            // 重试次数用尽，关闭连接并从缓存中移除
                            log.error("节点[{}]心跳连接失败，移除缓存并关闭连接", entry.getKey());
                            RpcBootstrap.CHANNEL_MAP.remove(entry.getKey());
                            // 关闭channel
                            if (channel.isOpen()) {
                                channel.close();
                            }
                        }
                        // 重试前随机等待一小段时间，避免雪崩
                        try {
                            Thread.sleep(10 * (1 + new Random().nextInt(5)));
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
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
            // 打印当前响应时间缓存内容
            for (Map.Entry<Long, Channel> entry : RpcBootstrap.RESPONSE_TIME_CHANNEL_MAP.entrySet()) {
                log.debug("响应时间：{}，对应节点：{}", entry.getKey(), entry.getValue());
            }
        }
    }
}
