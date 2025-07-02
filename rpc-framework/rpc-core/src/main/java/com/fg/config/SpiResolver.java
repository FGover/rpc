package com.fg.config;

import com.fg.compress.CompressorFactory;
import com.fg.compress.service.Compressor;
import com.fg.loadbalancer.service.LoadBalancer;
import com.fg.serialize.SerializerFactory;
import com.fg.serialize.service.Serializer;
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
    }
}
