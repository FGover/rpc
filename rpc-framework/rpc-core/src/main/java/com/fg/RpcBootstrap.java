package com.fg;

import com.fg.annotation.RpcService;
import com.fg.channel.handler.RpcRequestDecoder;
import com.fg.channel.handler.RpcRequestHandler;
import com.fg.channel.handler.RpcResponseEncoder;
import com.fg.config.Configuration;
import com.fg.discovery.RegistryConfig;
import com.fg.heartbeat.HeartBeatDetector;
import com.fg.heartbeat.RpcShutdownHook;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.transport.message.RpcRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;


@Slf4j
public class RpcBootstrap {
    // 饿汉式单例
    private static final RpcBootstrap rpcBootstrap = new RpcBootstrap();
    // 全局配置中心
    private final Configuration configuration;
    // 当前请求线程
    public static final ThreadLocal<RpcRequest> REQUEST_THREAD_LOCAL = new ThreadLocal<>();
    // 服务列表
    public static final Map<String, ServiceConfig<?>> SERVICE_LIST = new ConcurrentHashMap<>();
    // 连接缓存
    public static final Map<InetSocketAddress, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();
    // 响应时间缓存
    public static final ConcurrentSkipListMap<Long, Channel> RESPONSE_TIME_CHANNEL_MAP = new ConcurrentSkipListMap<>();
    // 定义全局的对外挂起的completableFuture
    public static final Map<Long, CompletableFuture<Object>> PENDING_REQUEST_MAP = new ConcurrentHashMap<>();

    // 私有化构造方法，防止外部实例化
    private RpcBootstrap() {
        configuration = new Configuration();
    }

    // 获取实例的静态方法
    public static RpcBootstrap getInstance() {
        return rpcBootstrap;
    }

    /**
     * 配置中心
     *
     * @return
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * 设置应用信息
     *
     * @param applicationName
     * @return
     */
    public RpcBootstrap application(String applicationName) {
        configuration.setApplicationName(applicationName);
        return this;
    }

    /**
     * 配置注册中心
     *
     * @param registryConfig
     * @return
     */
    public RpcBootstrap registry(RegistryConfig registryConfig) {
        configuration.setRegistryConfig(registryConfig);
        return this;
    }

    /**
     * 配置负载均衡器
     *
     * @param loadBalancer
     * @return
     */
    public RpcBootstrap loadBalancer(LoadBalancer loadBalancer) {
        configuration.setLoadBalancer(loadBalancer);
        return this;
    }

    /**
     * 发布服务（先启动netty后，再注册服务）
     *
     * @param service
     */
    public void publish(ServiceConfig<?> service) {
//        configuration.getRegistryConfig().getRegistry().register(service);
        SERVICE_LIST.put(service.getInterface().getName(), service);
    }

    /**
     * 发布多个服务
     *
     * @param services
     * @return
     */
    public RpcBootstrap publish(List<ServiceConfig<?>> services) {
        for (ServiceConfig<?> service : services) {
            this.publish(service);
        }
        return this;
    }

    /**
     * 启动Netty服务
     */
    public void start() {
        // 注册关闭应用程序的钩子函数
        Runtime.getRuntime().addShutdownHook(new RpcShutdownHook());
        // bossGroup 负责接收客户端连接，一个线程即可
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // workerGroup 负责处理客户端I/O事件，默认线程数为 CPU*2
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 创建Netty服务启动类
            ServerBootstrap bootstrap = new ServerBootstrap();
            // 配置启动参数
            bootstrap.group(bossGroup, workerGroup)  // 设置线程组
                    .channel(NioServerSocketChannel.class)  // 设置通道类型
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new LoggingHandler())      // 双向调试日志
                                    .addLast(new RpcRequestDecoder())   // [入站]解码器
                                    .addLast(new RpcResponseEncoder())  // [出站]编码器
                                    .addLast(new RpcRequestHandler());   // [入站]业务处理器 + 发出响应
                        }
                    });  // 设置通道初始化器
            // 绑定端口并同步阻塞知道绑定完成
            ChannelFuture channelFuture = bootstrap.bind(configuration.getPort()).sync();
            log.info("Netty服务{}已启动，监听端口：{}", configuration.getApplicationName(), configuration.getPort());
            // 端口就绪后，再向注册中心发布服务，避免已注册但未就绪的窗口
            try {
                if (configuration.getRegistryConfig() == null || configuration.getRegistryConfig().getRegistry() == null) {
                    log.info("未配置注册中心，无需发布服务");
                } else {
                    // 注册服务
                    SERVICE_LIST.values().forEach(service -> {
                        try {
                            configuration.getRegistryConfig().getRegistry().register(service);
                            log.info("服务已发布到注册中心：{}（group={}）", service.getInterface().getName(), service.getGroup());
                        } catch (Exception e) {
                            log.warn("服务注册失败：{}，err={}", service.getInterface().getName(), e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("批量服务注册异常：{}", e.getMessage());
            }
            // 阻塞直到服务通道关闭
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 关闭线程组
            try {
                bossGroup.shutdownGracefully().sync();
                workerGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 用于配置 consumer 引用的远程服务接口
     *
     * @param reference
     */
    public void reference(ReferenceConfig<?> reference) {
        // 设置consumer引用的远程服务接口的注册中心配置
        reference.setRegistry(configuration.getRegistryConfig().getRegistry());
        // 设置分组
        reference.setGroup(configuration.getGroup());
        // 开启心跳检测
        HeartBeatDetector.detect(reference.getInterface().getName());
    }

    /**
     * 设置服务分组
     *
     * @param groupName
     * @return
     */
    public RpcBootstrap group(String groupName) {
        configuration.setGroup(groupName);
        return this;
    }

    /**
     * 配置序列化方式
     *
     * @param serializerType
     * @return
     */
    public RpcBootstrap serializer(String serializerType) {
        log.debug("配置序列化方式:{}", serializerType);
        configuration.setSerializeType(serializerType);
        return this;
    }

    /**
     * 配置压缩方式
     *
     * @param compressType
     * @return
     */
    public RpcBootstrap compress(String compressType) {
        log.debug("配置压缩方式:{}", compressType);
        configuration.setCompressType(compressType);
        return this;
    }

    /**
     * 扫描指定包下所有使用了 @RpcService 注解的类，并将其注册为 RPC 服务
     *
     * @param packageName
     * @return
     */
    public RpcBootstrap scan(String packageName) {
        // 获取指定包名下的所有类的全限定类名
        List<String> classNames = getAllClassNames(packageName);
        classNames.stream().map(className -> {
                    try {
                        // 通过反射加载类对象
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        // 若找不到类，抛出运行时异常
                        throw new RuntimeException(e);
                    }
                })
                // 只报留标注了@RpcService注解的类
                .filter(clazz -> clazz.isAnnotationPresent(RpcService.class))
                .forEach(clazz -> {
                    try {
                        // 获取无参构造方法，并设置可访问
                        Constructor<?> constructor = clazz.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        // 通过反射创建类的实例
                        Object serviceInstance = constructor.newInstance();
                        // 获取该类实现的所有接口（一个服务类应至少实现一个接口）
                        Class<?>[] interfaces = clazz.getInterfaces();
                        if (interfaces.length == 0) {
                            throw new RuntimeException("类 " + clazz.getName() + " 未实现任何接口，无法注册为服务");
                        }
                        RpcService annotation = clazz.getAnnotation(RpcService.class);
                        // 获取分组
                        String group = annotation.group();
                        // 遍历所有接口，将每个接口作为一个服务注册
                        for (Class<?> anInterface : interfaces) {
                            // 创建服务配置对象
                            ServiceConfig<?> serviceConfig = new ServiceConfig<>();
                            serviceConfig.setInterface(anInterface);  // 设置服务接口
                            serviceConfig.setRef(serviceInstance);  // 设置服务实现类实例
                            serviceConfig.setGroup(group);           // 设置分组
                            // 发布服务
                            publish(serviceConfig);
                            log.info("服务注册成功: {} -> {}, group = {}", anInterface.getName(), clazz.getName(), group);
                        }
                    } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                             NoSuchMethodException e) {
                        throw new RuntimeException("服务类实例化失败：" + clazz.getName(), e);
                    }
                });
        return this;
    }

    /**
     * 获取指定包下所有类的全限定类名
     *
     * @param packageName
     * @return
     */
    private List<String> getAllClassNames(String packageName) {
        List<String> classNames = new ArrayList<>();
        // 将包名转换为路径
        String basePath = packageName.replace(".", "/");
        // 获取包对应的绝对路径（target/classes 目录下的路径）
        URL url = ClassLoader.getSystemClassLoader().getResource(basePath);
        if (url == null) {
            throw new RuntimeException("包路径不存在：" + packageName);
        }
        // 获取该路径对应的绝对文件系统路径
        String absolutePath = url.getPath();
        // 从该路径递归扫描 .class 文件
        recursionFile(new File(absolutePath), packageName, classNames);
        return classNames;
    }

    /**
     * 递归扫描文件目录，找出所有类的全限定名
     *
     * @param file
     * @param currentPackage
     * @param classNames
     */
    private void recursionFile(File file, String currentPackage, List<String> classNames) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            // 获取当前目录下的所有文件/目录
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    // 如果是子目录，递归处理，并拼接包名
                    if (child.isDirectory()) {
                        recursionFile(child, currentPackage + "." + child.getName(), classNames);
                    } else if (child.getName().endsWith(".class")) {
                        // 如果是 .class 文件，去掉后缀并拼接成全限定类名
                        String className = currentPackage + "." + child.getName().replace(".class", "");
                        classNames.add(className);
                    }
                }
            }
        }
    }

}
