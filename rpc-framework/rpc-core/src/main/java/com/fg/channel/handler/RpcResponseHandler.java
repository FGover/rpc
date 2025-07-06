package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import com.fg.enums.RequestType;
import com.fg.enums.ResponseCode;
import com.fg.transport.message.ResponsePayload;
import com.fg.transport.message.RpcRequest;
import com.fg.transport.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse) {
        log.info("哈哈哈哈哈哈哈==================：{}", rpcResponse);
        // 判断是否为关闭响应
        if (rpcResponse.getResponsePayload().getCode() == ResponseCode.CLOSING.getCode()) {
            log.warn("服务端正在关闭，响应被拒绝：{}", rpcResponse.getResponsePayload().getData());
            // 可以选择不触发 complete，或者触发异常 completeExceptionally()
            CompletableFuture<Object> future = RpcBootstrap.PENDING_REQUEST_MAP.remove(rpcResponse.getRequestId());
            if (future != null) {
                future.completeExceptionally(new RuntimeException("服务端正在关闭，无法处理请求"));
            }
            InetSocketAddress remoteAddress = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();
            RpcBootstrap.CHANNEL_MAP.remove(remoteAddress);
            log.info("已从CHANNEL_MAP中移除服务端：{}", remoteAddress);
            // 重新负载均衡
            RpcRequest request = RpcBootstrap.REQUEST_THREAD_LOCAL.get();
            RpcBootstrap.getInstance().getConfiguration().getLoadBalancer().reLoadBalance(
                    request.getRequestPayload().getInterfaceName(), RpcBootstrap.CHANNEL_MAP.keySet().stream().toList());
            return;
        }

        // 判断是否为心跳响应
        if (rpcResponse.getRequestType() == RequestType.HEARTBEAT.getId()) {
            log.info("收到心跳响应：{}", rpcResponse.getRequestId());
            CompletableFuture<Object> future = RpcBootstrap.PENDING_REQUEST_MAP.get(rpcResponse.getRequestId());
            if (future != null) {
                future.complete(null);
                RpcBootstrap.PENDING_REQUEST_MAP.remove(rpcResponse.getRequestId());
            }
            // 收到心跳响应，直接返回
            return;
        }
        // 服务提供方给的结果
        System.out.println("收到服务提供方返回的结果：" + rpcResponse);
        ResponsePayload responsePayload = rpcResponse.getResponsePayload();
        Object result = responsePayload.getData();  // 获取data
        // 从全局的挂起请求中寻找与之匹配的待处理的completableFuture
        CompletableFuture<Object> completableFuture = RpcBootstrap.PENDING_REQUEST_MAP.get(rpcResponse.getRequestId());
        System.out.println(completableFuture);
        if (completableFuture != null) {
            // 将结果放入completableFuture中，唤醒等待的线程
            completableFuture.complete(result);
            // 移除完成的请求
            RpcBootstrap.PENDING_REQUEST_MAP.remove(rpcResponse.getRequestId());
        }
    }

}
