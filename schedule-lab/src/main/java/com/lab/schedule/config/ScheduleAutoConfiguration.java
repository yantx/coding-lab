package com.lab.schedule.config;

import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.DistributedTaskScheduler;
import com.lab.schedule.core.lock.DistributedLockService;
import com.lab.schedule.core.manager.DistributedTaskManager;
import com.lab.schedule.core.registry.TaskRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 自动配置：创建 Executor / Scheduler / Manager / Registry
 */
@Configuration
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(prefix = "schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleAutoConfiguration {

    @Bean
    public DistributedTaskExecutor distributedTaskExecutor(DistributedLockService lockService) {
        return new DistributedTaskExecutor(lockService);
    }

    @Bean("monitorTaskScheduler")
    public TaskScheduler monitorTaskScheduler(SchedulerProperties schedulerProperties) {
        ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
        sched.setPoolSize(schedulerProperties.getThreadPoolSize());
        sched.setThreadNamePrefix("schedule-pool-");
        sched.setAwaitTerminationSeconds(30);
        sched.setWaitForTasksToCompleteOnShutdown(true);
        sched.initialize();
        return sched;
    }

    @Bean
    public DistributedTaskScheduler distributedTaskScheduler(@Qualifier("monitorTaskScheduler") TaskScheduler taskScheduler,
                                                             DistributedTaskExecutor executor) {
        return new DistributedTaskScheduler(taskScheduler, executor);
    }

    @Bean
    public DistributedTaskManager distributedTaskManager(DistributedTaskScheduler scheduler,
                                                         DistributedTaskExecutor executor) {
        return new DistributedTaskManager(scheduler, executor);
    }

    @Bean
    public TaskRegistry taskRegistry(ApplicationContext applicationContext,
                                     DistributedTaskManager taskManager) {
        return new TaskRegistry(applicationContext, taskManager);
    }
}
