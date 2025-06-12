package com.fg.discovery;

import com.fg.Constant;
import com.fg.discovery.Impl.NacosRegistry;
import com.fg.discovery.Impl.ZookeeperRegistry;
import com.fg.exception.DiscoveryException;

public class RegistryConfig {

    // 定义连接的url
    private String connectString;

    public RegistryConfig(String connectString) {
        this.connectString = connectString;
    }

    public Registry getRegistry() {
        // 根据url获取注册中心类型
        String registryType = getRegistryType(connectString, true).toLowerCase();
        if (registryType.equals("zookeeper")) {
            // 获取ip
            String host = getRegistryType(connectString, false);
            return new ZookeeperRegistry(host, Constant.TIME_OUT);
        } else if (registryType.equals("nacos")) {
            // 获取ip
            String host = getRegistryType(connectString, false);
            return new NacosRegistry(host, Constant.TIME_OUT);
        }
        throw new DiscoveryException("未找到注册中心");
    }

    /**
     * 获取注册中心类型
     *
     * @param connectString
     * @param isType
     * @return
     */
    private String getRegistryType(String connectString, boolean isType) {
        String[] typeAndHost = connectString.split("://");
        if (typeAndHost.length != 2) {
            throw new RuntimeException("connectString is illegal");
        }
        if (isType) {
            return typeAndHost[0];
        } else {
            return typeAndHost[1];
        }
    }
}
