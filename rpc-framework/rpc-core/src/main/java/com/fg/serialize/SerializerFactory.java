package com.fg.serialize;

import com.fg.serialize.service.Impl.HessianSerializer;
import com.fg.serialize.service.Impl.JdkSerializer;
import com.fg.serialize.service.Impl.JsonSerializer;
import com.fg.serialize.service.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态工厂方法
 */
@Slf4j
public class SerializerFactory {

    // 序列化器映射表
    private final static ConcurrentHashMap<String, SerializerWrapper<Serializer>>
            SERIALIZER_CACHE = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Byte, SerializerWrapper<Serializer>>
            SERIALIZER_CACHE_CODE = new ConcurrentHashMap<>();


    // 静态初始化，添加已实现的方式
    static {
        SerializerWrapper<Serializer> jdkWrapper = new SerializerWrapper<>((byte) 1, "jdk", new JdkSerializer());
        SerializerWrapper<Serializer> jsonWrapper = new SerializerWrapper<>((byte) 2, "json", new JsonSerializer());
        SerializerWrapper<Serializer> hessianWrapper = new SerializerWrapper<>((byte) 3, "hessian", new HessianSerializer());

        SERIALIZER_CACHE.put("jdk", jdkWrapper);
        SERIALIZER_CACHE.put("json", jsonWrapper);
        SERIALIZER_CACHE.put("hessian", hessianWrapper);

        SERIALIZER_CACHE_CODE.put((byte) 1, jdkWrapper);
        SERIALIZER_CACHE_CODE.put((byte) 2, jsonWrapper);
        SERIALIZER_CACHE_CODE.put((byte) 3, hessianWrapper);
    }

    // 根据序列化名称获取序列化器包装
    public static SerializerWrapper<Serializer> getSerializer(String serializeType) {
        SerializerWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE.get(serializeType);
        if (serializerWrapper == null) {
            log.error("未找到您配置的{}序列化工具，默认选用jdk的序列化方式", serializeType);
            return getDefaultSerializer();
        }
        return serializerWrapper;
    }

    // 根据序列化编号获取序列化包装
    public static SerializerWrapper<Serializer> getSerializer(Byte serializeCode) {
        SerializerWrapper<Serializer> serializerWrapper = SERIALIZER_CACHE_CODE.get(serializeCode);
        if (serializerWrapper == null) {
            log.error("未找到您配置的{}序列化工具，默认选用jdk的序列化方式", serializeCode);
            return getDefaultSerializer();
        }
        return serializerWrapper;
    }

    // 获取默认的jdk序列化器包装
    public static SerializerWrapper<Serializer> getDefaultSerializer() {
        return SERIALIZER_CACHE.get("jdk");
    }

    // 运行时新增序列化器
    public static void addSerializer(SerializerWrapper<Serializer> serializerWrapper) {
        SERIALIZER_CACHE.put(serializerWrapper.getName(), serializerWrapper);
        SERIALIZER_CACHE_CODE.put(serializerWrapper.getCode(), serializerWrapper);
    }
}
