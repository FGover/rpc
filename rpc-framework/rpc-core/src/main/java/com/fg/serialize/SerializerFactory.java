package com.fg.serialize;

import com.fg.config.ObjectWrapper;
import com.fg.serialize.service.impl.HessianSerializer;
import com.fg.serialize.service.impl.JdkSerializer;
import com.fg.serialize.service.impl.JsonSerializer;
import com.fg.serialize.service.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态工厂方法
 */
@Slf4j
public class SerializerFactory {

    // 序列化器映射表
    private final static Map<String, ObjectWrapper<Serializer>>
            SERIALIZER_CACHE = new ConcurrentHashMap<>();
    private final static Map<Byte, ObjectWrapper<Serializer>>
            SERIALIZER_CACHE_CODE = new ConcurrentHashMap<>();


    // 静态初始化，添加已实现的方式
    static {
        ObjectWrapper<Serializer> jdkWrapper = new ObjectWrapper<>((byte) 1, "jdk", new JdkSerializer());
        ObjectWrapper<Serializer> jsonWrapper = new ObjectWrapper<>((byte) 2, "json", new JsonSerializer());
        ObjectWrapper<Serializer> hessianWrapper = new ObjectWrapper<>((byte) 3, "hessian", new HessianSerializer());

        SERIALIZER_CACHE.put("jdk", jdkWrapper);
        SERIALIZER_CACHE.put("json", jsonWrapper);
        SERIALIZER_CACHE.put("hessian", hessianWrapper);

        SERIALIZER_CACHE_CODE.put((byte) 1, jdkWrapper);
        SERIALIZER_CACHE_CODE.put((byte) 2, jsonWrapper);
        SERIALIZER_CACHE_CODE.put((byte) 3, hessianWrapper);
    }

    // 根据序列化名称获取序列化器包装
    public static ObjectWrapper<Serializer> getSerializer(String serializeType) {
        ObjectWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE.get(serializeType);
        if (serializerWrapper == null) {
            log.error("未找到您配置的{}序列化工具，默认选用jdk的序列化方式", serializeType);
            return getDefaultSerializer();
        }
        return serializerWrapper;
    }

    // 根据序列化编号获取序列化包装
    public static ObjectWrapper<Serializer> getSerializer(Byte serializeCode) {
        ObjectWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE_CODE.get(serializeCode);
        if (serializerWrapper == null) {
            log.error("未找到您配置的{}序列化工具，默认选用jdk的序列化方式", serializeCode);
            return getDefaultSerializer();
        }
        return serializerWrapper;
    }

    // 获取默认的jdk序列化器包装
    public static ObjectWrapper<Serializer> getDefaultSerializer() {
        return SERIALIZER_CACHE.get("jdk");
    }

    // 添加序列化器
    public static void addSerializer(ObjectWrapper<Serializer> serializerWrapper) {
        SERIALIZER_CACHE.put(serializerWrapper.getType(), serializerWrapper);
        SERIALIZER_CACHE_CODE.put(serializerWrapper.getCode(), serializerWrapper);
    }
}
