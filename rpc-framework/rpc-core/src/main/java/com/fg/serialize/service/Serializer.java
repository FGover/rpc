package com.fg.serialize.service;

public interface SerializerService {

    // 序列化，用于将请求/响应变成可以传输的字节
    byte[] serialize(Object object);

    // 反序列化，用于接收到字节后恢复成对象
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
