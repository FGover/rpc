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
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Map<Long, Object> IDEMPOTENT_CACHE = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest) throws Exception {
        log.info("收到请求ID: {}", rpcRequest.getRequestId());
        // 幂等性验证
        if (IDEMPOTENT_CACHE.containsKey(rpcRequest.getRequestId())) {
            log.debug("检测到重复请求，返回缓存结果：{}", rpcRequest.getRequestId());
            Object cachedResult = IDEMPOTENT_CACHE.get(rpcRequest.getRequestId());
            // 返回缓存的结果
            RpcResponse rpcResponse = new RpcResponse();
            rpcResponse.setRequestId(rpcRequest.getRequestId());
            rpcResponse.setRequestType(rpcRequest.getRequestType());
            rpcResponse.setResponsePayload(
                    ResponsePayload.builder()
                            .code(ResponseCode.SUCCESS.getCode())
                            .data(cachedResult)
                            .build()
            );
            channelHandlerContext.writeAndFlush(rpcResponse);
            return;
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
                        IDEMPOTENT_CACHE.put(rpcRequest.getRequestId(),
                                rpcResponse.getResponsePayload().getData());
                    }
                } catch (Exception ignore) {
                }
            } catch (Exception e) {
                log.error("处理请求{}失败", rpcRequest, e);
                // 构造失败响应体
                rpcResponse.setResponsePayload(
                        ResponsePayload.builder()
                                .code(ResponseCode.FAILURE.getCode())
                                .data("调用服务失败: " + e.getMessage())
                                .build()
                );
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
