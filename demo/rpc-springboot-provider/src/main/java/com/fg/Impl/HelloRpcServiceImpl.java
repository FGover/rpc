package com.fg.Impl;

import com.fg.HelloRpcService;
import com.fg.annotation.RpcService;

@RpcService(group = "main")
public class HelloRpcServiceImpl implements HelloRpcService {
    @Override
    public String sayHello(String msg) {
        return "Hi Consumer：" + msg;
    }

    @Override
    public String getIdempotentTest(String input) {
        return "";
    }
}
