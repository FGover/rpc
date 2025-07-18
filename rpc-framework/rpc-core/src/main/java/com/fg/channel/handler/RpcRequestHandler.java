package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.enums.RequestType;
import com.fg.enums.ResponseCode;
import com.fg.heartbeat.ShutdownHolder;
import com.fg.protection.limiter.service.impl.TokenBucketLimiter;
import com.fg.transport.message.RequestPayload;
import com.fg.transport.message.ResponsePayload;
import com.fg.transport.message.RpcRequest;
import com.fg.transport.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

@Slf4j
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcRequest rpcRequest) throws Exception {
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
        // 1.判断是否为心跳请求
        if (rpcRequest.getRequestType() == RequestType.HEARTBEAT.getId()) {
            log.info("收到心跳请求：{}", rpcRequest.getRequestId());
            rpcResponse.setResponsePayload(
                    ResponsePayload.builder()
                            .code(ResponseCode.SUCCESS.getCode())
                            .data("心跳响应")
                            .build()
            );
            channelHandlerContext.writeAndFlush(rpcResponse);
            return;
        }
        // 2.限流逻辑：尝试获取令牌
        TokenBucketLimiter limiter = RpcBootstrap.getInstance().getConfiguration().getLimiter();
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
        log.info("服务端写入响应：{}", rpcResponse);
        channelHandlerContext.writeAndFlush(rpcResponse);
        // 计数器减1
        ShutdownHolder.REQUEST_COUNT.decrement();
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
        Object ref = serviceConfig.getRef();
        try {
            Method method = ref.getClass().getMethod(methodName, parametersType);
            return method.invoke(ref, parametersValue);
        } catch (Exception e) {
            throw new RuntimeException("方法调用失败", e);
        }
    }
}
