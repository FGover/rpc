package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.CompletableFuture;

public class MySimpleChannelInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf msg)
            throws Exception {
        // 服务提供方给的结果
        String result = msg.toString(CharsetUtil.UTF_8);
        // 从全局的挂起请求中寻找与之匹配的待处理的completableFuture
        CompletableFuture<Object> completableFuture = RpcBootstrap.PENDING_REQUEST_MAP.get(1L);
        completableFuture.complete(result);
    }

}
