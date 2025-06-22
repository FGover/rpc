package com.fg.loadbalancer.service.Impl;

import com.fg.RpcBootstrap;
import com.fg.loadbalancer.AbstractLoadBalancer;
import com.fg.loadbalancer.service.Selector;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

@Slf4j
public class MinimumResponseTimeLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected Selector getSelector(List<InetSocketAddress> serviceList) {
        return new MinimumResponseTimeSelector(serviceList);
    }

    private static class MinimumResponseTimeSelector implements Selector {
        // 服务列表
        private final List<InetSocketAddress> serviceList;

        private MinimumResponseTimeSelector(List<InetSocketAddress> serviceList) {
            this.serviceList = serviceList;
        }

        /**
         * 选择响应时间最小的服务节点
         *
         * @return
         */
        @Override
        public InetSocketAddress selectServiceInstance() {
            // 遍历响应时间排序的Channel Map，选响应时间最小且服务列表中包含的节点
            log.info("开始选择响应时间最小的服务节点");
            log.info("服务列表：{}", serviceList);
            for (Map.Entry<Long, Channel> entry : RpcBootstrap.RESPONSE_TIME_CHANNEL_MAP.entrySet()) {
                Channel channel = entry.getValue();
                InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
                log.info("当前服务节点: {}，响应时间: {}", remoteAddress, entry.getKey());
                if (serviceList.contains(remoteAddress)) {
                    log.info("最小响应算法：选中服务节点: {}，响应时间: {}", remoteAddress, entry.getKey());
                    return remoteAddress;
                }
            }
            // 若无匹配则返回第一个节点或null
            if (!serviceList.isEmpty()) {
                log.warn("无符合条件的服务节点，默认返回第一个节点: {}", serviceList.get(0));
                return serviceList.get(0);
            }
            return null;
        }
    }

}
