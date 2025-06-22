package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import com.fg.enums.RequestType;
import com.fg.transport.message.ResponsePayload;
import com.fg.transport.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse) {
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
