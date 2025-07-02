package com.fg;

import com.fg.annotation.Idempotent;

public interface HelloRpcService {

    @Idempotent(maxRetry = 3, retryIntervalMs = 1000)
    String sayHello(String msg);
}
