package com.fg.channel;

import com.fg.channel.handler.RpcRequestDecoder;
import com.fg.channel.handler.RpcRequestHandler;
import com.fg.channel.handler.RpcResponseEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;

public class ProviderChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast(new LoggingHandler())      // 双向调试日志
                .addLast(new RpcRequestDecoder())   // [入站]解码器
                .addLast(new RpcResponseEncoder())  // [出站]编码器
                .addLast(new RpcRequestHandler());   // [入站]业务处理器 + 发出响应
    }
}
