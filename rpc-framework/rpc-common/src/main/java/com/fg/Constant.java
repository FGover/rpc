package com.fg;

public class Constant {

    // zookeeper 默认连接地址
    public static final String DEFAULT_ZK_CONNECT = "127.0.0.1:2181";

    // zookeeper 默认超时时间
    public static final Integer TIME_OUT = 10000;

    // 服务提供方和调用方在注册中心的基础路径
    public static final String BASE_PROVIDERS_PATH = "/rpc-metadata/provider";
    public static final String BASE_CONSUMERS_PATH = "/rpc-metadata/consumer";

}
