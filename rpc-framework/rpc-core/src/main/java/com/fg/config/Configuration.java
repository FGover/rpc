package com.fg.config;

import com.fg.IdGenerator;
import com.fg.discovery.RegistryConfig;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.protection.CircuitBreaker;
import com.fg.protection.limiter.service.impl.TokenBucketLimiter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局配置类
 */
@Data
@Slf4j
public class Configuration {
    // 默认端口
    private int port = 8088;
    // 定义基础配置
    private String applicationName;
    // 注册中心
    private RegistryConfig registryConfig;
    // 序列化类型
    private String serializeType;
    // 压缩类型
    private String compressType;
    // 定义全局的ID生成器
    private IdGenerator idGenerator;
    // 负载均衡器
    private LoadBalancer loadBalancer;
    // 限流器
    private TokenBucketLimiter limiter = new TokenBucketLimiter(10, 5);
    // 熔断器
    private CircuitBreaker circuitBreaker = new CircuitBreaker(3, 2, 5000);
    // 分组
    private String group = "default";

    public Configuration() {
        // 先用spi加载接口的实现类
        SpiResolver spiResolver = new SpiResolver();
        spiResolver.loadFromSpi(this);
        // 再从xml中加载配置
        XmlResolver xmlResolver = new XmlResolver();
        xmlResolver.loadMergedConfig(this);
    }

}
