package com.fg.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;

public class AppClient {

    private final String host;
    private final int port;

    public AppClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() throws InterruptedException {
        // 创建一个线程组，用于处理网络事件
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            // 创建Bootstrap实例，用于启动客户端
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)  // 设置线程组
                    .channel(NioSocketChannel.class)  // 指定使用NIO的SocketChannel
                    .remoteAddress(new InetSocketAddress(8080))  // 设置服务器的IP地址和端口号
                    .handler(new ClientChannelInitializer());
            // 发起连接请求，并同步阻塞直到连接完成
            ChannelFuture channelFuture = bootstrap.connect().sync();
            // 获取channel，并写出数据
            channelFuture.channel().writeAndFlush(Unpooled.copiedBuffer("Hello Netty", CharsetUtil.UTF_8));
            // 等待连接关闭
            channelFuture.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new AppClient("127.0.0.1", 8080).run();
    }
}
