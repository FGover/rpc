package com.fg.serialize.service.Impl;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.fg.serialize.service.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.IOException;

@Slf4j
public class HessianSerializer implements Serializer {

    @Override
    public byte[] serialize(Object object) {
        log.info("使用hessian序列化");
        if (object == null) {
            throw new IllegalArgumentException("序列化对象不能为空");
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Hessian2Output output = new Hessian2Output(bos);
            output.writeObject(object);
            output.flush();   // 保证数据写出
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("处理序列化异常", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        log.info("使用hessian反序列化");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("反序列化对象不能为空");
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            Hessian2Input input = new Hessian2Input(bis);
            Object obj = input.readObject();
            return clazz.cast(obj);
        } catch (IOException e) {
            log.error("处理反序列化异常", e);
            throw new RuntimeException(e);
        }
    }
}
