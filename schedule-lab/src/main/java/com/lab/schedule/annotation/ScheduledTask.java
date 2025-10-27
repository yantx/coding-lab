package com.lab.schedule.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 分布式任务调度注解
 * 用于定义需要定时执行的任务，支持分布式环境下的任务调度。
 * 同一分组的任务会按顺序执行，不同分组的任务可以并行执行。
 * 支持分布式锁确保集群中只有一个实例执行任务。
 * 支持SpEL表达式和属性占位符。
 *
 * <p>使用示例：
 * <pre class="code">
 * {@code @ScheduledTask(name = "#{@environment.getProperty('app.name')}-dataSync", 
 *     group = "${app.task.group}", 
 *     cron = "${app.task.cron.expression}", 
 *     description = "数据同步任务")}
 * public void syncData() {
 *     // 任务实现
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScheduledTask {
    /**
     * 任务名称，支持SpEL表达式，如: "#{@environment.getProperty('app.task.prefix')}-dataSync"
     */
    @AliasFor("name")
    String value() default "";
    
    /**
     * 任务名称，支持SpEL表达式，如: "#{@environment.getProperty('app.task.prefix')}-dataSync"
     */
    @AliasFor("value")
    String name() default "";
    
    /**
     * 任务分组，支持SpEL表达式和属性占位符，同一分组的任务会按顺序执行，默认为"default"
     */
    String group() default "default";
    
    /**
     * 任务执行顺序，同一分组内的任务按order值从小到大顺序执行，默认为0
     * 值越小优先级越高，执行越靠前
     */
    int order() default 0;
    
    /**
     * cron表达式，支持SpEL表达式和属性占位符
     * 例如: "${app.task.cron.expression}" 或 "#{@environment.getProperty('app.task.cron.expression')}"
     * 与fixedRate和fixedDelay互斥
     */
    String cron() default "";
    
    /**
     * 固定速率执行，单位毫秒
     * 与cron和fixedDelay互斥
     */
    long fixedRate() default -1;
    
    /**
     * 固定速率执行，支持SpEL表达式和属性占位符
     * 例如: "${app.task.fixed.rate}" 或 "#{@environment.getProperty('app.task.fixed.rate', T(java.lang.Long).valueOf(60000))}"
     * 与cron和fixedDelay互斥
     */
    String fixedRateString() default "";

    /**
     * 固定延迟执行，单位毫秒
     * 与cron和fixedRate互斥
     */
    long fixedDelay() default -1;
    
    /**
     * 固定延迟执行，支持SpEL表达式和属性占位符
     * 例如: "${app.task.fixed.delay}" 或 "#{@environment.getProperty('app.task.fixed.delay', T(java.lang.Long).valueOf(5000))}"
     * 与cron和fixedRate互斥
     */
    String fixedDelayString() default "";
    
    /**
     * 初始延迟时间，单位毫秒，默认为0
     * 支持SpEL表达式和属性占位符
     */
    String initialDelayString() default "";
    
    /**
     * 初始延迟时间，单位毫秒，默认为0
     */
    long initialDelay() default 0;
    
    /**
     * 是否异步执行，如果为true，则任务会在单独的线程中执行，默认为false
     */
    boolean async() default false;

    /**
     * 任务描述信息
     */
    String description() default "";
    
    /**
     * 任务是否启用，如果为false，则任务不会被调度执行，默认为true
     */
    boolean enabled() default true;
    
    /**
     * 任务执行失败时的最大重试次数，默认为0（不重试）
     */
    int maxRetries() default 0;
    
    /**
     * 重试间隔时间，单位毫秒，默认为5000（5秒）
     */
    long retryDelay() default 5000;
    
    /**
     * 任务执行超时时间，单位毫秒
     * 0表示不超时，默认为0
     */
    long timeout() default 0;
    
    /**
     * 是否使用分布式锁，如果为true，则确保集群中只有一个实例执行该任务，默认为true
     */
    boolean distributed() default true;
    
    /**
     * 分布式锁的持有时间，单位秒
     * 应设置为大于任务预期执行时间的值，默认为300（5分钟）
     */
    long lockHoldTime() default 300;
}
