package com.fg.protection;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器：主要是客户端使用，用于保护调用方（即客户端）在服务端不稳定时避免雪崩，快速失败，防止反复调用失败服务导致资源耗尽。
 * CLOSED     => 正常状态，请求正常通过，统计失败率
 * OPEN       => 熔断状态，所有请求立即失败（不调用远程服务）
 * HALF_OPEN  => 半开状态，允许部分请求试探服务是否恢复
 */
@Slf4j
public class CircuitBreaker {
    // 熔断阈值：失败次数达到该值就熔断
    private final int failureThreshold;
    // 半开状态允许尝试的成功次数
    private final int successThreshold;
    // 半开阶段仅允许单并发探测
    private final AtomicBoolean halfOpenPermit = new AtomicBoolean(true);
    // 熔断后等待多少毫秒后进入半开状态
    private final long timeout;
    // 当前失败次数计数器
    private final AtomicInteger failureCount = new AtomicInteger(0);
    // 当前成功次数计数器（用于HALF_OPEN状态）
    private final AtomicInteger successCount = new AtomicInteger(0);
    // 熔断开始时间
    private volatile long lastFailureTime = -1;
    // 熔断器状态
    private volatile State state = State.CLOSED;

    // 枚举定义状态
    private enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    // 构造器：传入失败阈值、成功阈值、熔断等待时间
    public CircuitBreaker(int failureThreshold, int successThreshold, long timeout) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeout = timeout;
    }

    /**
     * 熔断器保护方法：包装对远程调用或容易出错代码的访问
     *
     * @param supplier 带异常的 lambda 或方法
     * @return 执行结果
     * @throws Exception 调用失败抛出的异常（包括熔断拒绝）
     */
    public synchronized <T> T call(SupplierWithException<T> supplier) throws Exception {
        switch (state) {
            case OPEN:
                // 如果当前已熔断，并且超过了超时时间，则进入半开状态
                if (System.currentTimeMillis() - lastFailureTime >= timeout) {
                    state = State.HALF_OPEN;
                    log.info("熔断器进入半开状态");
                } else {
                    // 否则直接拒绝请求
                    throw new Exception("熔断器已打开，拒绝请求！");
                }
            case HALF_OPEN:
                // 并发限流：已有人在探测则拒绝
                if (!halfOpenPermit.compareAndSet(true, false)) {
                    throw new Exception("熔断器半开状态，正在探测中，拒绝并发请求！");
                }
                // 半开状态允许一定数量的请求探测服务是否恢复
                try {
                    T result = supplier.get();  // 正常调用远程服务
                    int success = successCount.incrementAndGet();
                    if (success >= successThreshold) {
                        reset();   // 如果连续成功达到阈值，则关闭熔断器
                        log.info("熔断器关闭");
                    }
                    return result;
                } catch (Exception e) {
                    trip();  // 如果失败，重新熔断
                    throw e;
                } finally {
                    halfOpenPermit.set(true);  // 释放并发限流
                }
            case CLOSED:
            default:
                // CLOSED 状态的逻辑：正常调用、统计失败次数、触发熔断
                try {
                    T result = supplier.get();   // 正常执行
                    failureCount.set(0);  // 成功则清空失败计数器
                    return result;
                } catch (Exception e) {
                    int failures = failureCount.incrementAndGet();
                    log.info("熔断器当前失败次数：{}", failures);
                    if (failures >= failureThreshold) {
                        trip();   // 熔断器触发
                        throw new RuntimeException("熔断器触发，已熔断！");
                    }
                    throw e;
                }
        }
    }

    /**
     * 熔断：将状态设为 OPEN，记录熔断时间，并重置计数器
     */
    private void trip() {
        state = State.OPEN;
        lastFailureTime = System.currentTimeMillis();
        failureCount.set(0);
        successCount.set(0);
        log.info("熔断器已打开，等待 {} 毫秒后进入半开状态", timeout);
    }

    /**
     * 重置：将熔断器恢复到 CLOSED 状态，重置计数器
     */
    private void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
    }


    /**
     * 支持带异常的函数接口
     *
     * @param <T>
     */
    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }

}
