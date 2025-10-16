package com.fg.Impl;

import com.fg.HelloRpcService2;
import com.fg.annotation.RpcService;

@RpcService
public class HelloRpcServiceImpl2 implements HelloRpcService2 {

    @Override
    public String sayHello2(String msg) {
        return "Hi Consumer：哈哈哈" + msg;
    }
}
