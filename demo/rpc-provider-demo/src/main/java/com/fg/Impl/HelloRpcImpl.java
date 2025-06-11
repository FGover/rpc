package com.fg.Impl;

import com.fg.HelloRpc;

public class HelloRpcImpl implements HelloRpc {
    @Override
    public String sayHello(String msg) {
        return "Hi Consumer：" + msg;
    }
}
