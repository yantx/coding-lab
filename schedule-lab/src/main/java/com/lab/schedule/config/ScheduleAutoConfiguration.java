package com.lab.schedule.config;

import com.lab.schedule.core.DistributedTaskScheduler;
import com.lab.schedule.core.TaskRegistry;
import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.executor.OrderedTaskExecutor;
import com.lab.schedule.core.lock.DistributedLockService;
import com.lab.schedule.core.registry.TaskRegistryService;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;

/**
 * 定时任务自动配置类
 * 负责配置任务调度器、分布式锁、任务注册等核心组件
 */
@Configuration
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(prefix = "schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleAutoConfiguration {

    private final SchedulerProperties schedulerProperties;

    public ScheduleAutoConfiguration(SchedulerProperties schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public TaskRegistry taskRegistry(DistributedTaskExecutor distributedTaskExecutor,
                                   TaskRegistryService taskRegistryService) {
        return new TaskRegistry(distributedTaskExecutor, taskRegistryService, schedulerProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockService distributedLockService(RedissonClient redissonClient) {
        return new DistributedLockService(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRegistryService taskRegistryService(DistributedLockService lockService,
                                                   RedisTemplate<String, Object> redisTemplate) {
        return new TaskRegistryService(lockService,redisTemplate);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DistributedTaskExecutor distributedTaskExecutor(
            TaskScheduler taskScheduler,
            DistributedLockService lockService) {
        return new DistributedTaskExecutor(lockService, taskScheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedTaskScheduler distributedTaskScheduler(
            TaskRegistry taskRegistry,
            DistributedTaskExecutor taskExecutor,
            OrderedTaskExecutor orderedTaskExecutor) {
        return new DistributedTaskScheduler(taskRegistry, taskExecutor, orderedTaskExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrderedTaskExecutor orderedTaskExecutor(
            RedissonClient redissonClient,
            DistributedLockService lockService,
            TaskRegistry taskRegistry,
            DistributedTaskExecutor distributedTaskExecutor) {
        return new OrderedTaskExecutor(redissonClient, lockService, taskRegistry, distributedTaskExecutor);
    }

}
