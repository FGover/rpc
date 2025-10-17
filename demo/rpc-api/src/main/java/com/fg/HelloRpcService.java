package com.fg;

import com.fg.annotation.Idempotent;

public interface HelloRpcService {

    String sayHello(String msg);

    @Idempotent(maxRetry = 3, retryIntervalMs = 1000)
    String getIdempotentTest(String input);

}
