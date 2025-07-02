package com.fg.loadbalancer.service.impl;

import com.fg.RpcBootstrap;
import com.fg.loadbalancer.AbstractLoadBalancer;
import com.fg.loadbalancer.service.Selector;
import com.fg.transport.message.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
public class ConsistentHashLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected Selector getSelector(List<InetSocketAddress> serviceList) {
        return new ConsistentHashSelector(serviceList, 500);
    }

    private static class ConsistentHashSelector implements Selector {
        /**
         * 哈希环：用于存储虚拟节点与真实节点的映射关系
         * key：虚拟节点的哈希值
         * value：对应的真实服务节点地址
         */
        private final SortedMap<Integer, InetSocketAddress> circle = new TreeMap<>();
        // 虚拟节点数量，用于增强负载均衡和防止数据倾斜
        private final int virtualNodes;

        // 命中计数器
        private final Map<InetSocketAddress, Integer> hitCountMap = new HashMap<>();


        public ConsistentHashSelector(List<InetSocketAddress> serviceList, int virtualNodes) {
            this.virtualNodes = virtualNodes;
            // 将所有真实节点添加到哈希环中，每个真实节点对应多个虚拟节点
            for (InetSocketAddress address : serviceList) {
                addNodeToCircle(address);
                hitCountMap.put(address, 0); // 初始化命中数为0
            }
        }

        /**
         * 一致性哈希算法：选取服务实例
         *
         * @return
         */
        @Override
        public InetSocketAddress selectServiceInstance() {
            // 从线程上下文中获取当前请求
            RpcRequest request = RpcBootstrap.REQUEST_THREAD_LOCAL.get();
            // 获取请求ID，转为字符串作为哈希的输入
            String requestId = request != null ? Long.toString(request.getRequestId()) : null;
            if (requestId == null) {
                // 如果请求id不存在，则选择哈希环上的第一个节点作为兜底
                log.error("请求上下文或请求ID为空，返回哈希环第一个节点");
                return circle.get(circle.firstKey());
            }
            // 对请求id做哈希计算，得到一个整数哈希值
            int hash = hash(requestId);
            // 如果哈希值对应的节点不存在，则找到哈希环中顺时针方向第一个节点
            if (!circle.containsKey(hash)) {
                // tailMap 返回的是 key >= hash 的子map
                SortedMap<Integer, InetSocketAddress> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            InetSocketAddress selectedNode = circle.get(hash);
            // 命中计数 + 打印
            hitCountMap.put(selectedNode, hitCountMap.getOrDefault(selectedNode, 0) + 1);
            System.out.println("当前节点命中情况：" + hitCountMap);
            if (log.isDebugEnabled()) {
                log.debug("一致性哈希选择器：请求ID [{}] 经过哈希 [{}] 选中节点 [{}]", requestId, hash, selectedNode);
            }
            return selectedNode;
        }

        /**
         * 将真实节点挂载到哈希环上，生成多个虚拟节点
         *
         * @param address
         */
        private void addNodeToCircle(InetSocketAddress address) {
            for (int i = 0; i < virtualNodes; i++) {
                int hash = hash(address.toString() + "-" + i);
                circle.put(hash, address);
            }
        }

        /**
         * 从哈希环中移除节点
         *
         * @param address
         */
        private void removeNodeFromCircle(InetSocketAddress address) {
            for (int i = 0; i < virtualNodes; i++) {
                int hash = hash(address.toString() + "-" + i);
                circle.remove(hash);
            }
        }

        /**
         * 具体的hash算法，基于MD5算法的前4字节转换成int值
         *
         * @param s 输入字符串
         * @return 正整数hash值
         */
        private int hash(String s) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            byte[] digest = md.digest(s.getBytes());
            int res = 0;
            for (int i = 0; i < 4; i++) {
                res <<= 8;
                res |= (digest[i] & 0xff);
            }
            // 保证为非负数，防止TreeMap负数影响排序
            return res & Integer.MAX_VALUE;
        }
    }

}
