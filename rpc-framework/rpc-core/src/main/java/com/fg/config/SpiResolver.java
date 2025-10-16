package com.fg.config;

import com.fg.compressor.CompressorFactory;
import com.fg.compressor.service.Compressor;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.protection.limiter.service.RateLimiter;
import com.fg.serializer.SerializerFactory;
import com.fg.serializer.service.Serializer;
import com.fg.spi.SpiHandler;

import java.util.List;

public class SpiResolver {

    /**
     * SPI加载配置
     *
     * @param configuration
     */
    public void loadFromSpi(Configuration configuration) {

        List<ObjectWrapper<LoadBalancer>> loadBalancerWrappers = SpiHandler.getAll(LoadBalancer.class);
        if (!loadBalancerWrappers.isEmpty()) {
            configuration.setLoadBalancer(loadBalancerWrappers.get(0).getImpl());
        }

        List<ObjectWrapper<Compressor>> compressorWrappers = SpiHandler.getAll(Compressor.class);
        if (!compressorWrappers.isEmpty()) {
            compressorWrappers.forEach(CompressorFactory::addCompressor);
        }

        List<ObjectWrapper<Serializer>> serializerWrappers = SpiHandler.getAll(Serializer.class);
        if (!serializerWrappers.isEmpty()) {
            serializerWrappers.forEach(SerializerFactory::addSerializer);
        }

        List<ObjectWrapper<RateLimiter>> limiterWrappers = SpiHandler.getAll(RateLimiter.class);
        if (!limiterWrappers.isEmpty()) {
            configuration.setLimiter(limiterWrappers.get(0).getImpl());
        }
    }
}
