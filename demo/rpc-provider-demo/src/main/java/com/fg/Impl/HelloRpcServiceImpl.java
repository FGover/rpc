package com.fg.Impl;

import com.fg.HelloRpcService;
import com.fg.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RpcService(group = "main")
public class HelloRpcServiceImpl implements HelloRpcService {
    @Override
    public String sayHello(String msg) {
        return "Hi Consumer：哈哈哈" + msg;
    }

    private static final AtomicInteger FAILS = new AtomicInteger(0);

    @Override
    public String getIdempotentTest(String input) {
        int n = FAILS.incrementAndGet();
        if (n <= 2) { // 前两次抛异常，触发客户端重试
            throw new RuntimeException("模拟业务失败，第 " + n + " 次失败");
        }
        return "幂等性测试成功:" + input;
    }

}
