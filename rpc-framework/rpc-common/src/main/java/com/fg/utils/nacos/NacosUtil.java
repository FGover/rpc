package com.fg.utils.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fg.Constant;
import com.fg.exception.DiscoveryException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Properties;

@Slf4j
public class NacosUtil {

    // 默认连接
    public static NamingService createNamingService() {
        String serverAddr = "127.0.0.1:8848";
        return createNamingService(serverAddr, Constant.TIME_OUT);
    }

    /**
     * 创建Nacos命名服务实例
     *
     * @param serverAddr
     * @param timeout
     * @return
     */
    public static NamingService createNamingService(String serverAddr, int timeout) {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", "public");
            properties.put("timeout", String.valueOf(timeout));
            NamingService namingService = NacosFactory.createNamingService(properties);
            log.info("Nacos客户端连接成功: {}", serverAddr);
            return namingService;
        } catch (NacosException e) {
            throw new DiscoveryException("创建Nacos客户端失败");
        }
    }

    /**
     * 注册服务实例
     *
     * @param namingService
     * @param node
     */
    public static void registerInstance(NamingService namingService, NacosNode node) {
        try {
            Instance instance = new Instance();
            instance.setIp(node.getIp());
            instance.setPort(node.getPort());
            instance.setHealthy(node.isHealthy());
            instance.setWeight(node.getWeight());
            namingService.registerInstance(node.getServiceName(), node.getGroup(), instance);
            log.info("服务注册成功：{} -> {}(group：{})", node.getServiceName(), node.getIp(), node.getGroup());
        } catch (NacosException e) {
            log.error("注册服务失败: {}", node.getServiceName(), e);
            throw new DiscoveryException("注册服务失败");
        }
    }

    /**
     * 获取服务实例列表
     *
     * @param namingService
     * @param serviceName
     * @param group
     * @return
     */
    public static List<NacosNode> getInstances(NamingService namingService, String serviceName, String group) {
        try {
            List<Instance> instances = namingService.selectInstances(serviceName, group, true);
            List<NacosNode> nodes = instances.stream()
                    .map(instance -> new NacosNode(
                            serviceName,
                            group,
                            instance.getIp(),
                            instance.getPort(),
                            instance.isHealthy(),
                            instance.getWeight()
                    )).toList();
            log.info("发现服务实例: {} (group: {}) -> {}", serviceName, group, nodes);
            return nodes;
        } catch (NacosException e) {
            log.error("发现服务实例失败: {}", serviceName, e);
            throw new DiscoveryException("发现服务实例失败");
        }
    }

    /**
     * 在“查询实例”的同时，完成订阅
     *
     * @param namingService
     * @param serviceName
     * @param group
     * @param listener
     * @return
     */
    public static List<NacosNode> getInstances(NamingService namingService, String serviceName, String group,
                                               EventListener listener) {
        // 先订阅
        subscribe(namingService, serviceName, group, listener);
        // 再获取健康实例列表
        return getInstances(namingService, serviceName, group);
    }

    /**
     * 监听服务实例变化
     *
     * @param namingService
     * @param serviceName
     * @param group
     * @param listener
     */
    public static void subscribe(NamingService namingService, String serviceName, String group, EventListener listener) {
        try {
            namingService.subscribe(serviceName, group, listener);
            log.info("开始监听服务: {} (group: {})", serviceName, group);
        } catch (NacosException e) {
            log.error("监听服务失败: {}", serviceName, e);
            throw new DiscoveryException("监听服务失败");
        }
    }

    public static void unsubscribe(NamingService namingService, String serviceName, String group, EventListener listener) {
        try {
            namingService.unsubscribe(serviceName, group, listener);
            log.info("取消订阅服务: {} (group: {})", serviceName, group);
        } catch (NacosException e) {
            log.error("取消订阅失败: {}", serviceName, e);
            throw new DiscoveryException("取消订阅失败");
        }
    }

    public static void close(NamingService namingService) {
        try {
            namingService.shutDown();
            log.info("关闭Nacos客户端成功");
        } catch (NacosException e) {
            log.error("关闭Nacos客户端失败", e);
            throw new DiscoveryException("关闭Nacos客户端失败");
        }
    }
}
