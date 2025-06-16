package com.fg.transport.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 请求调用方法所请求的接口方法的描述
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequestPayload implements Serializable {

    // 接口名字（要调用的接口的全限定类名：com.fg.HelloRpcService）
    private String interfaceName;
    // 方法名字（要调用的方法名：sayHello）
    private String methodName;
    // 方法的参数类型数组
    private Class<?>[] parametersType;
    // 方法的参数值数组
    private Object[] parametersValue;
    // 返回值类型
    private Class<?> returnType;
}
