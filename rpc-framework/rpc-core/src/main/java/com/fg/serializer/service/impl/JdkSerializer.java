package com.fg.serializer.service.impl;

import com.fg.serializer.service.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
public class JdkSerializer implements Serializer {

    /**
     * 序列化
     *
     * @param object
     * @return
     */
    @Override
    public byte[] serialize(Object object) {
        log.info("使用jdk序列化");
        if (object == null) {
            throw new IllegalArgumentException("序列化对象不能为空");
        }
        try (
                // 用于接收序列化后的字节数据
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                // jdk 提供的对象序列化流（写入对象）
                ObjectOutputStream oos = new ObjectOutputStream(bos)
        ) {
            oos.writeObject(object);  // 将对象写入输出流
            return bos.toByteArray(); // 将输出流转换为字节数组
        } catch (IOException e) {
            log.error("处理序列化异常", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 反序列化
     *
     * @param bytes
     * @param clazz
     * @param <T>
     * @return
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        log.info("使用jdk反序列化");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化对象不能为空");
        }
        try (
                // 将字节数组包装为输入流
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                // jdk 提供的对象反序列化流（读取对象）
                ObjectInputStream ois = new ObjectInputStream(bis)
        ) {
            Object obj = ois.readObject();  // 读取对象
            return clazz.cast(obj); // 将对象类型转换并返回
        } catch (IOException | ClassNotFoundException e) {
            log.error("处理反序列化异常", e);
            throw new RuntimeException(e);
        }
    }

}
