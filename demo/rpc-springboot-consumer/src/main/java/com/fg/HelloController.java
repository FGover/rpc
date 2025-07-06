package com.fg;

import com.fg.annotation.RpcApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RpcApiService
    private HelloRpcService helloService;

    @GetMapping("/rpc/hello")
    public String sayHello() {
        return helloService.sayHello("全名制作人大家好，我是蔡徐坤，我喜欢唱跳rap篮球！");
    }
}
