package com.lab.schedule.core;

import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 仅负责调度（与框架交互），不处理锁逻辑
 */
public class DistributedTaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DistributedTaskScheduler.class);

    private final TaskScheduler taskScheduler;
    private final DistributedTaskExecutor executor;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public DistributedTaskScheduler(TaskScheduler taskScheduler, DistributedTaskExecutor executor) {
        this.taskScheduler = taskScheduler;
        this.executor = executor;
    }

    public void scheduleTask(TaskDomain task) {
        if (task == null) return;
        String name = task.getName();
        cancelTask(name);

        if (task.isCronBased()) {
            String cron = task.getCron();
            if (cron == null || cron.trim().isEmpty()) throw new IllegalArgumentException("cron 不能为空");
            Trigger trigger = new CronTrigger(cron);
            ScheduledFuture<?> f = taskScheduler.schedule(executor.createTaskRunnable(task), trigger);
            scheduledTasks.put(name, f);
            logger.info("已调度cron任务 {} cron={}", name, cron);
            return;
        }
        if (task.isFixedRate()) {
            long period = task.getFixedRate();
            long initial = task.getInitialDelay();
            ScheduledFuture<?> f = taskScheduler.scheduleAtFixedRate(executor.createTaskRunnable(task), Instant.now().plusMillis(initial), Duration.ofMillis(period));
            scheduledTasks.put(name, f);
            logger.info("已调度固定频率任务 {} period={} initial={}", name, period, initial);
            return;
        }
        if (task.isFixedDelay()) {
            long delay = task.getFixedDelay();
            long initial = task.getInitialDelay();
            ScheduledFuture<?> f = taskScheduler.scheduleWithFixedDelay(executor.createTaskRunnable(task), Instant.now().plusMillis(initial), Duration.ofMillis(delay));
            scheduledTasks.put(name, f);
            logger.info("已调度固定延迟任务 {} delay={} initial={}", name, delay, initial);
            return;
        }

        logger.warn("未找到任务 {} 的有效调度配置", name);
    }

    public void cancelTask(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskName);
        if (future != null) {
            future.cancel(true);
            logger.info("已取消任务: {}", taskName);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduledTasks.values().forEach(f -> f.cancel(false));
        scheduledTasks.clear();
        logger.info("DistributedTaskScheduler 已关闭");
    }
}
