package com.fg.discovery;

import com.fg.ServiceConfig;

public interface Registry {

    // 注册服务
    void register(ServiceConfig<?> serviceConfig);
}
