package com.lab.schedule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分布式调度器配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "schedule")
public class SchedulerProperties {
    /**
     * 是否启用调度器
     */
    private boolean enabled = true;

    private List<String> ceshi;
    
    /**
     * 任务执行线程池大小
     */
    private int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    
    /**
     * 获取线程池大小
     * @return 线程池大小
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    /**
     * 设置线程池大小
     * @param threadPoolSize 线程池大小
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * 任务组配置
     */
    private Map<String, GroupConfig> groups = new HashMap<>();

    /**
     * 任务组配置类
     */
    @Data
    public static class GroupConfig {
        /**
         * 该任务组允许的最大并发任务数
         */
        private int concurrency = 1;

        /**
         * 是否为此任务组启用分布式锁
         */
        private boolean distributed = true;
    }

}
