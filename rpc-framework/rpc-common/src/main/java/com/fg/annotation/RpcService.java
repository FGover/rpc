package com.fg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)   // 指定RpcService注解可以应用于类、接口（包括注解类型）、枚举类型
@Retention(RetentionPolicy.RUNTIME)   // 表示该注解在运行时仍然可用，可以通过反射机制读取
public @interface RpcService {
    // 分组字段
    String group() default "default";
}
