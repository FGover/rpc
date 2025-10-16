package com.fg.loadbalancer.service.impl;

import com.fg.loadbalancer.AbstractLoadBalancer;
import com.fg.loadbalancer.service.Selector;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RoundRobinLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected Selector getSelector(List<InetSocketAddress> serviceList) {
        return new RoundRobinSelector(serviceList);
    }

    private static class RoundRobinSelector implements Selector {
        // 服务列表
        private final List<InetSocketAddress> serviceList;
        // 当前索引
        private final AtomicInteger index = new AtomicInteger(0);

        public RoundRobinSelector(List<InetSocketAddress> serviceList) {
            this.serviceList = serviceList;
        }

        /**
         * 轮询算法选取
         *
         * @return
         */
        @Override
        public InetSocketAddress selectServiceInstance() {
            int current = index.getAndIncrement();
            int pos = current & Integer.MAX_VALUE;
            // 使用模运算实现循环轮询
            int selectedIndex = pos % serviceList.size();
            InetSocketAddress selectedAddress = serviceList.get(selectedIndex);
            log.debug("轮询选择器选中地址: {}，索引: {}", selectedAddress, selectedIndex);
            return selectedAddress;
        }
    }
}
