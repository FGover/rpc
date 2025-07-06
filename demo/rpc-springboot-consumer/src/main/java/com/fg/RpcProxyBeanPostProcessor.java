package com.fg;

import com.fg.annotation.RpcApiService;
import com.fg.proxy.ProxyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@Slf4j
public class RpcProxyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取bean的所有字段
        Field[] fields = bean.getClass().getDeclaredFields();
        for (Field field : fields) {
            // 判断字段是否标注了@RpcApiService注解
            if (field.isAnnotationPresent(RpcApiService.class)) {
                field.setAccessible(true);  // 设置可访问私有字段
                // 获取字段类型
                Class<?> interfaceClass = field.getType();
                // 创建代理对象
                Object proxy = ProxyFactory.createProxy(interfaceClass);
                try {
                    field.setAccessible(true);
                    field.set(bean, proxy);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("注入代理失败: " + field.getName(), e);
                }
            }
        }
        return bean;
    }
}
