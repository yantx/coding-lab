package com.lab.schedule.core.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务组执行器管理器
 * 负责管理不同任务组的线程池执行器，确保同组任务在同一个线程池中顺序执行
 */
@Component
public class TaskGroupExecutorManager {
    private static final Logger log = LoggerFactory.getLogger(TaskGroupExecutorManager.class);
    private final Map<String, ThreadPoolTaskScheduler> executors = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ScheduledFuture<?>>> groupTasks = new ConcurrentHashMap<>();

    /**
     * 获取或创建任务组的执行器
     * @param group 任务组名称
     * @return 线程池任务调度器
     */
    public synchronized ThreadPoolTaskScheduler getOrCreateExecutor(String group) {
        return executors.computeIfAbsent(group, this::createExecutor);
    }

    /**
     * 创建单线程的线程池执行器
     * @param group 任务组名称
     * @return 线程池任务调度器
     */
    private ThreadPoolTaskScheduler createExecutor(String group) {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(1); // 单线程执行器，确保任务顺序执行
        executor.setThreadFactory(new TaskGroupThreadFactory(group));
        executor.setThreadNamePrefix("task-group-" + group + "-");
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        
        log.info("Created executor for task group: {}", group);
        return executor;
    }
    
    /**
     * 注册任务到任务组
     * @param group 任务组
     * @param taskName 任务名称
     * @param future 调度future
     */
    public void registerTask(String group, String taskName, ScheduledFuture<?> future) {
        groupTasks.computeIfAbsent(group, k -> new ConcurrentHashMap<>())
                 .put(taskName, future);
    }
    
    /**
     * 取消任务
     * @param group 任务组
     * @param taskName 任务名称
     * @return 如果任务存在并成功取消返回true，否则返回false
     */
    public boolean cancelTask(String group, String taskName) {
        Map<String, ScheduledFuture<?>> tasks = groupTasks.get(group);
        if (tasks != null) {
            ScheduledFuture<?> future = tasks.remove(taskName);
            if (future != null) {
                boolean cancelled = future.cancel(false);
                if (cancelled) {
                    log.info("Cancelled task: {} in group: {}", taskName, group);
                }
                return cancelled;
            }
        }
        return false;
    }

    /**
     * 关闭所有执行器
     */
    public synchronized void shutdown() {
        log.info("Shutting down all task group executors...");
        
        // 先取消所有任务
        for (Map.Entry<String, Map<String, ScheduledFuture<?>>> entry : groupTasks.entrySet()) {
            for (Map.Entry<String, ScheduledFuture<?>> taskEntry : entry.getValue().entrySet()) {
                try {
                    taskEntry.getValue().cancel(false);
                } catch (Exception e) {
                    log.error("Error cancelling task: {} in group: {}", taskEntry.getKey(), entry.getKey(), e);
                }
            }
        }
        groupTasks.clear();
        
        // 关闭所有执行器
        for (Map.Entry<String, ThreadPoolTaskScheduler> entry : executors.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.info("Shutdown executor for group: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Error shutting down executor for group: " + entry.getKey(), e);
            }
        }
        executors.clear();
        
        log.info("All task group executors have been shut down");
    }
    
    /**
     * 自定义线程工厂，用于设置线程名称和异常处理器
     */
    private static class TaskGroupThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final String groupName;

        TaskGroupThreadFactory(String groupName) {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.groupName = groupName;
            this.namePrefix = "task-group-" + groupName + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            
            // 设置异常处理器
            t.setUncaughtExceptionHandler((thread, e) -> {
                log.error("Uncaught exception in task thread: " + thread.getName(), e);
            });
            
            // 设置守护线程
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            
            // 设置优先级
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            
            return t;
        }
    }
}
