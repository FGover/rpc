package com.fg.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class AppServer {

    private final int port;

    public AppServer(int port) {
        this.port = port;
    }

    public void run() throws InterruptedException {
        // bossGroup：处理客户端连接（只处理 accept 事件）
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        // workerGroup：处理已连接通道的读写（处理读写事件）
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 服务器启动类
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)  // 设置两个线程组
                    .channel(NioServerSocketChannel.class) // 使用 NIO 的 ServerSocketChannel
                    .childHandler(new ServerChannelInitializer());
            // 绑定端口并启动服务器
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            System.out.println("Server started on port " + port);
            channelFuture.channel().closeFuture().sync();
        } finally {
            // 优雅关闭线程组
            bossGroup.shutdownGracefully().sync();
            workerGroup.shutdownGracefully().sync();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new AppServer(8080).run();
    }
}
