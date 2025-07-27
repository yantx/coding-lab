package com.lab.feign.palm;

import org.springframework.cloud.netflix.feign.FeignClient;
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

    @AliasFor(annotation = FeignClient.class, attribute = "fallback")
    Class<?> fallback() default void.class;

    @AliasFor(annotation = FeignClient.class, attribute = "fallbackFactory")
    Class<?> fallbackFactory() default void.class;

    @AliasFor(annotation = FeignClient.class, attribute = "path")
    String path() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "contextId")
    String contextId() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "decode404")
    boolean decode404() default false;

    @AliasFor(annotation = FeignClient.class, attribute = "configuration")
    Class<?> configuration() default void.class;

    @AliasFor(annotation = FeignClient.class, attribute = "primary")
    boolean primary() default true;

    @AliasFor(annotation = FeignClient.class, attribute = "qualifier")
    String qualifier() default "";

}