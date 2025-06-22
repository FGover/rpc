package com.fg.channel.handler;

import com.fg.RpcBootstrap;
import com.fg.ServiceConfig;
import com.fg.enums.RequestType;
import com.fg.enums.ResponseCode;
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
        // 1.判断是否为心跳请求
        if (rpcRequest.getRequestType() == RequestType.HEARTBEAT.getId()) {
            log.info("收到心跳请求：{}", rpcRequest.getRequestId());
            channelHandlerContext.writeAndFlush(rpcResponse);
            return;
        }
        try {
            // 2.处理请求，调用目标方法
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
        // 3.发送完整的响应对象
        log.info("服务端写入响应：{}", rpcResponse);
        channelHandlerContext.writeAndFlush(rpcResponse);
    }

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
