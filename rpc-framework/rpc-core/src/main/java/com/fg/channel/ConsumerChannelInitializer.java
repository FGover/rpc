package com.fg.channel;

import com.fg.channel.handler.MySimpleChannelInboundHandler;
import com.fg.channel.handler.RpcMessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class ConsumerChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline()
                .addLast(new LoggingHandler(LogLevel.DEBUG))  // netty自带的日志处理器
                .addLast(new RpcMessageEncoder())  // 消息编码器
                .addLast(new MySimpleChannelInboundHandler());
    }
}
