package com.lab.feign.palm;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @Author:
 * @Date: 2025-04-29 21:21:01
 * @Description:
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FeignClient // 继承原生注解能力
public @interface CustomFeignClient {
    @AliasFor(annotation = FeignClient.class, attribute = "value")
    String value() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "name")
    String name() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "url")
    String url() default "";
}