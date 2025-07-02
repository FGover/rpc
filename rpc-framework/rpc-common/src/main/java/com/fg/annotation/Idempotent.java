package com.fg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    // 最大重试次数
    int maxRetry() default 3;
    // 重试间隔时间
    long retryIntervalMs() default 1000;
}
