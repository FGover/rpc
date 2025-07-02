package com.fg.serialize.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.fg.serialize.service.Serializer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonSerializer implements Serializer {

    @Override
    public byte[] serialize(Object object) {
        log.info("使用json序列化");
        if (object == null) {
            throw new IllegalArgumentException("序列化对象不能为空");
        }
        try {
            // fastjson2 序列化为 JSON 字节数组
            return JSON.toJSONBytes(object);
        } catch (Exception e) {
            log.error("处理序列化异常", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        log.info("使用json反序列化");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化对象不能为空");
        }
        try {
            // fastjson2 反序列化字节数组为 Java 对象
            return JSON.parseObject(bytes, clazz, JSONReader.Feature.SupportClassForName);
        } catch (Exception e) {
            log.error("处理反序列化异常", e);
            throw new RuntimeException(e);
        }
    }
}
