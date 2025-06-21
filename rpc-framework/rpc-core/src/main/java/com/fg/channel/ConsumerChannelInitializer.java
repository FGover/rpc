package com.fg.channel;

import com.fg.channel.handler.RpcResponseHandler;
import com.fg.channel.handler.RpcRequestEncoder;
import com.fg.channel.handler.RpcResponseDecoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class ConsumerChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline()
                .addLast(new LoggingHandler(LogLevel.DEBUG))  // 双向调试日志
                .addLast(new RpcRequestEncoder())  // [出站]消息编码器
                .addLast(new RpcResponseDecoder())  // [入站]消息解码器
                .addLast(new RpcResponseHandler());   // [入站]业务处理器 + 处理响应
    }
}
