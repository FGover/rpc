package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.annotation.Idempotent;
import com.fg.enums.RequestType;
import com.fg.enums.ResponseCode;
import com.fg.heartbeat.ShutdownHolder;
import com.fg.protection.limiter.service.RateLimiter;
import com.fg.transport.message.RequestPayload;
import com.fg.transport.message.ResponsePayload;
import com.fg.transport.message.RpcRequest;
import com.fg.transport.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

    // 缓存请求ID和结果
    private static final Map<Long, CacheEntry> IDEMPOTENT_CACHE = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idempotent-cache-cleanup-thread");
        t.setDaemon(true);
        return t;
    });

    // 缓存配置
    private static final int MAX_CACHE_SIZE = 1000;  // 最大缓存数量
    private static final long CACHE_EXPIRE_MINUTES = 30;  // 缓存过期时间（分钟）

    static {
        // 启动定时清理任务，每5分钟清理一次过期缓存
        CLEANUP_EXECUTOR.scheduleAtFixedRate(RpcRequestHandler::cleanExpiredCache, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 缓存条目：包含数据和过期时间
     */
    private static class CacheEntry {
        @Getter
        private final Object data;
        @Getter
        private final boolean success;  // 是否成功
        private final long expireTime;  // 过期时间

        public CacheEntry(Object data, boolean success, long expireTime) {
            this.data = data;
            this.success = success;
            this.expireTime = expireTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    /**
     * 清理过期缓存
     */
    private static void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, CacheEntry>> iterator = IDEMPOTENT_CACHE.entrySet().iterator();
        int removedCount = 0;
        while (iterator.hasNext()) {
            Map.Entry<Long, CacheEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }
        // 如果缓存数量超过限制，删除最旧的（FIFO策略）
        if (IDEMPOTENT_CACHE.size() > MAX_CACHE_SIZE) {
            int toRemove = IDEMPOTENT_CACHE.size() - MAX_CACHE_SIZE;
            Iterator<Map.Entry<Long, CacheEntry>> it = IDEMPOTENT_CACHE.entrySet().iterator();
            for (int i = 0; i < toRemove && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
            removedCount += toRemove;
        }
        if (removedCount > 0) {
            log.debug("清理幂等缓存，移除 {} 条记录，当前缓存大小: {}", removedCount, IDEMPOTENT_CACHE.size());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest) throws Exception {
        log.info("收到请求ID: {}", rpcRequest.getRequestId());
        // 幂等性验证
        CacheEntry cacheEntry = IDEMPOTENT_CACHE.get(rpcRequest.getRequestId());
        if (cacheEntry != null) {
            // 检查是否过期
            if (cacheEntry.isExpired()) {
                IDEMPOTENT_CACHE.remove(rpcRequest.getRequestId());
                log.debug("缓存已过期，移除：{}", rpcRequest.getRequestId());
            } else {
                log.debug("检测到重复请求，返回缓存结果：{}", rpcRequest.getRequestId());
                // 返回缓存的结果
                RpcResponse rpcResponse = new RpcResponse();
                rpcResponse.setRequestId(rpcRequest.getRequestId());
                rpcResponse.setRequestType(rpcRequest.getRequestType());
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(cacheEntry.isSuccess() ? ResponseCode.SUCCESS.getCode() : ResponseCode.FAILURE.getCode())
                                .data(cacheEntry.getData())
                                .build()
                );
                channelHandlerContext.writeAndFlush(rpcResponse);
                return;
            }
        }
        log.info("收到请求：{}，进行请求处理...", rpcRequest);
        // 封装响应对象
        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setRequestId(rpcRequest.getRequestId());
        rpcResponse.setRequestType(rpcRequest.getRequestType());
        rpcResponse.setCompressType(rpcRequest.getCompressType());
        rpcResponse.setSerializeType(rpcRequest.getSerializeType());
        // 查看关闭的挡板是否打开，如果挡板已经打开，返回关闭响应
        if (ShutdownHolder.BAFFLE.get()) {
            rpcResponse.setResponsePayload(
                    ResponsePayload
                            .builder()
                            .code(ResponseCode.CLOSING.getCode())
                            .data("服务端正在关闭")
                            .build()
            );
            channelHandlerContext.writeAndFlush(rpcResponse);
            return;
        }
        // 计数器加1
        ShutdownHolder.REQUEST_COUNT.increment();
        try {
            // 1.判断是否为心跳请求
            if (rpcRequest.getRequestType() == RequestType.HEARTBEAT.getId()) {
                log.debug("收到心跳请求：{}", rpcRequest.getRequestId());
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(ResponseCode.SUCCESS.getCode())
                                .data("心跳响应")
                                .build()
                );
                channelHandlerContext.writeAndFlush(rpcResponse);
                return;
            }
            // 2.限流器
            RateLimiter limiter = RpcBootstrap.getInstance().getConfiguration().getLimiter();
            if (!limiter.tryAcquire()) {
                log.error("请求{}被限流", rpcRequest.getRequestId());
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(ResponseCode.FAILURE.getCode())
                                .data("请求被限流")
                                .build()
                );
                channelHandlerContext.writeAndFlush(rpcResponse);
                return;
            }
            try {
                // 3.处理请求，调用目标方法
                RequestPayload requestPayload = rpcRequest.getRequestPayload();
                // 根据负载内容进行方法调用
                Object result = callTargetMethod(requestPayload);
                // 构造响应体
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(ResponseCode.SUCCESS.getCode())
                                .data(result)
                                .build()
                );
                try {
                    RequestPayload pl = rpcRequest.getRequestPayload();
                    String interfaceName = pl.getInterfaceName();
                    String methodName = pl.getMethodName();
                    Class<?>[] paramTypes = pl.getParametersType();
                    Object ref = RpcBootstrap.SERVICE_LIST.get(interfaceName).getRef();
                    boolean idempotent =
                            ref.getClass().getMethod(methodName, paramTypes).isAnnotationPresent(Idempotent.class)
                                    || (ref.getClass().getInterfaces().length > 0
                                    && ref.getClass().getInterfaces()[0].getMethod(methodName, paramTypes)
                                    .isAnnotationPresent(Idempotent.class));

                    if (idempotent) {
                        // 将本次结果写入幂等缓存（顶部已有 IDEMPOTENT_CACHE 读取逻辑）
                        long expireTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CACHE_EXPIRE_MINUTES);
                        CacheEntry entry = new CacheEntry(
                                rpcResponse.getResponsePayload().getData(),
                                true,
                                expireTime
                        );
                        IDEMPOTENT_CACHE.put(rpcRequest.getRequestId(), entry);
                        log.debug("缓存幂等结果，requestId: {}, 过期时间: {} 分钟后", rpcRequest.getRequestId(), CACHE_EXPIRE_MINUTES);
                    }
                } catch (Exception ignore) {
                }
            } catch (Exception e) {
                log.error("处理请求{}失败", rpcRequest, e);
                // 构造失败响应体
                String errorMsg = "调用服务失败: " + e.getMessage();
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(ResponseCode.FAILURE.getCode())
                                .data(errorMsg)
                                .build()
                );
                // 缓存失败结果（避免重复执行失败操作）
                try {
                    RequestPayload pl = rpcRequest.getRequestPayload();
                    String interfaceName = pl.getInterfaceName();
                    String methodName = pl.getMethodName();
                    Class<?>[] paramTypes = pl.getParametersType();
                    Object ref = RpcBootstrap.SERVICE_LIST.get(interfaceName).getRef();
                    boolean idempotent =
                            ref.getClass().getMethod(methodName, paramTypes).isAnnotationPresent(Idempotent.class)
                                    || (ref.getClass().getInterfaces().length > 0
                                    && ref.getClass().getInterfaces()[0].getMethod(methodName, paramTypes)
                                    .isAnnotationPresent(Idempotent.class));

                    if (idempotent) {
                        long expireTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CACHE_EXPIRE_MINUTES);
                        CacheEntry entry = new CacheEntry(
                                errorMsg,
                                false,  // 失败
                                expireTime
                        );
                        IDEMPOTENT_CACHE.put(rpcRequest.getRequestId(), entry);
                        log.debug("缓存幂等失败结果，requestId: {}", rpcRequest.getRequestId());
                    }
                } catch (Exception ignore) {
                    // 忽略缓存失败的情况
                }
            }
            // 4.发送完整的响应对象
            log.debug("服务端写入响应：{}", rpcResponse);
            channelHandlerContext.writeAndFlush(rpcResponse);
        } finally {
            // 计数器减1
            ShutdownHolder.REQUEST_COUNT.decrement();
        }
    }

    /**
     * 调用目标服务的方法
     *
     * @param requestPayload
     * @return
     */
    private Object callTargetMethod(RequestPayload requestPayload) {
        String interfaceName = requestPayload.getInterfaceName();
        String methodName = requestPayload.getMethodName();
        Class<?>[] parametersType = requestPayload.getParametersType();
        Object[] parametersValue = requestPayload.getParametersValue();
        ServiceConfig<?> serviceConfig = RpcBootstrap.SERVICE_LIST.get(interfaceName);
        if (serviceConfig == null) {
            throw new RuntimeException("未找到服务：" + interfaceName);
        }
        Object ref = serviceConfig.getRef();
        try {
            Method method = ref.getClass().getMethod(methodName, parametersType);
            return method.invoke(ref, parametersValue);
        } catch (Exception e) {
            throw new RuntimeException("方法调用失败", e);
        }
    }
}
