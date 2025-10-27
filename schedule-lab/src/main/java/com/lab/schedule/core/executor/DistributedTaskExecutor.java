package com.lab.schedule.core.executor;

import com.lab.schedule.core.lock.DistributedLockService;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 分布式任务执行器
 * 负责在分布式环境中执行定时任务，确保任务按照预定的时间表执行，
 * 并且在整个集群中只有一个实例执行每个任务。
 */
public class DistributedTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DistributedTaskExecutor.class);

    private final DistributedLockService lockService;   // 分布式锁服务
    private final TaskScheduler taskScheduler;         // 任务调度器

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();  // 已调度的任务
    private final Map<String, TaskExecution> taskExecutions = new ConcurrentHashMap<>();      // 执行中的任务

    /**
     * 构造函数
     * @param lockService 分布式锁服务
     * @param taskScheduler 任务调度器
     */
    public DistributedTaskExecutor(DistributedLockService lockService, TaskScheduler taskScheduler) {
        this.lockService = lockService;
        this.taskScheduler = taskScheduler;
        logger.info("分布式任务执行器初始化完成");
    }

    /**
     * 调度任务执行
     *
     * @param task 要调度的任务信息
     */
    public void scheduleTask(TaskDomain task) {
        if (task.isCronBased()) {
            scheduleCronTask(task);
        } else if (task.isFixedRate()) {
            scheduleFixedRateTask(task);
        } else if (task.isFixedDelay()) {
            scheduleFixedDelayTask(task);
        } else {
            logger.warn("未找到任务 {} 的有效调度配置", task.getName());
        }
    }

    /**
     * 使用cron表达式调度任务
     */
    private void scheduleCronTask(TaskDomain task) {
        String taskName = task.getName();
        String cronExpression = task.getCron();

        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("任务 " + taskName + " 的cron表达式不能为空");
        }

        // 如果任务已存在，先取消
        cancelTask(taskName);

        // 创建cron触发器
        Trigger trigger = new CronTrigger(cronExpression);

        // 调度任务
        ScheduledFuture<?> future = taskScheduler.schedule(
                createTaskRunnable(task),
                trigger
        );

        scheduledTasks.put(taskName, future);
        logger.info("已调度cron任务: {}, cron表达式: {}", taskName, cronExpression);
    }

    /**
     * 调度固定频率任务
     */
    private void scheduleFixedRateTask(TaskDomain task) {
        String taskName = task.getName();
        long period = task.getFixedRate();
        long initialDelay = task.getInitialDelay();

        if (period <= 0) {
            throw new IllegalArgumentException("任务 " + taskName + " 的固定执行间隔必须大于0");
        }

        // 如果任务已存在，先取消
        cancelTask(taskName);

        // 调度任务
        Instant startTime = Instant.now().plusMillis(initialDelay);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                createTaskRunnable(task),
                Instant.now().plusMillis(initialDelay),
                Duration.ofMillis(period)
        );

        scheduledTasks.put(taskName, future);
        logger.info("已调度固定频率任务: {}, 执行间隔: {}毫秒, 初始延迟: {}毫秒",
                taskName, period, initialDelay);
    }

    /**
     * 调度固定延迟任务
     */
    private void scheduleFixedDelayTask(TaskDomain task) {
        String taskName = task.getName();
        long delay = task.getFixedDelay();
        long initialDelay = task.getInitialDelay();

        if (delay <= 0) {
            throw new IllegalArgumentException("任务 " + taskName + " 的固定延迟时间必须大于0");
        }

        // 如果任务已存在，先取消
        cancelTask(taskName);

        // 调度任务
        Instant startTime = Instant.now().plusMillis(initialDelay);
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                createTaskRunnable(task),
                Instant.now().plusMillis(initialDelay),
                Duration.ofMillis(delay)
        );

        scheduledTasks.put(taskName, future);
        logger.info("已调度固定延迟任务: {}, 延迟时间: {}毫秒, 初始延迟: {}毫秒",
                taskName, delay, initialDelay);
    }

    /**
     * 创建任务Runnable，处理分布式锁逻辑
     * @param taskInfo 任务信息
     * @return 可运行的任务
     */
    public Runnable createTaskRunnable(TaskDomain taskInfo) {
        return () -> {
            String taskName = taskInfo.getName();
            String lockKey = "schedule:task:lock:" + taskName;

            // 如果任务已经在运行，则跳过
            if (isTaskRunning(taskName)) {
                logger.trace("任务 {} 已在运行，跳过本次执行", taskName);
                return;
            }

            // 尝试获取分布式锁
            boolean locked = false;
            try {
                // 默认锁定时间为 60 秒，可以根据需要调整
                long lockHoldTime = 60;
                locked = lockService.tryLock(lockKey, 0, lockHoldTime, java.util.concurrent.TimeUnit.SECONDS);
                if (!locked) {
                    logger.trace("无法获取任务 {} 的分布式锁，跳过执行", taskName);
                    return;
                }

                // 标记任务为运行中
                TaskExecution execution = new TaskExecution(taskInfo);
                taskExecutions.put(taskName, execution);

                try {
                    // 执行任务
                    logger.debug("开始执行任务: {}", taskName);
                    taskInfo.markStarted();
                    taskInfo.execute();
                    taskInfo.markCompleted();
                    logger.debug("任务执行完成: {}", taskName);

                } catch (Exception e) {
                    taskInfo.markFailed(e.getMessage());
                    logger.error("任务执行失败: " + taskName, e);

                } finally {
                    // 清理任务状态
                    taskExecutions.remove(taskName);
                }

            } catch (Exception e) {
                logger.error("任务执行包装器发生错误: " + taskName, e);

            } finally {
                // 确保释放锁
                if (locked) {
                    try {
                        lockService.unlock(lockKey);
                    } catch (Exception e) {
                        logger.error("释放任务 " + taskName + " 的分布式锁失败", e);
                    }
                }
            }
        };
    }

    /**
     * 检查任务是否正在运行中
     *
     * @param taskName 任务名称
     * @return 如果任务正在运行返回true，否则返回false
     */
    public boolean isTaskRunning(String taskName) {
        TaskExecution execution = taskExecutions.get(taskName);
        return execution != null && execution.isRunning();
    }

    /**
     * 取消任务
     *
     * @param taskName 任务名称
     */
    public void cancelTask(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskName);
        if (future != null) {
            future.cancel(true);
            logger.info("已取消任务: {}", taskName);
        } else {
            logger.warn("取消任务失败，未找到任务: {}", taskName);
        }
    }


    /**
     * 恢复任务
     *
     * @param taskInfo 任务信息
     */
    public void resumeTask(TaskDomain taskInfo) {
        if (taskInfo == null) {
            logger.warn("恢复任务失败，任务信息不能为空");
            return;
        }

        // 如果任务已经存在，先取消
        if (scheduledTasks.containsKey(taskInfo.getName())) {
            cancelTask(taskInfo.getName());
        }

        // 重新调度任务
        scheduleTask(taskInfo);
        logger.info("已恢复任务: {}", taskInfo.getName());
    }

    /**
     * 立即触发任务执行
     *
     * @param taskName 要触发的任务名称
     * @return 如果任务存在并成功触发返回true，否则返回false
     */
    public boolean triggerTask(String taskName) {
        // 实际实现取决于任务如何存储和检索
        // 这里是一个简化版本
        logger.info("手动触发任务: {}", taskName);
        return true;
    }

    /**
     * 暂停任务
     *
     * @param taskName 要暂停的任务名称
     * @return 如果任务存在并成功暂停返回true，否则返回false
     */
    public boolean pauseTask(String taskName) {
        // 实际实现取决于任务如何存储和检索
        // 这里是一个简化版本
        logger.info("暂停任务: {}", taskName);
        return true;
    }

    /**
     * 恢复已暂停的任务
     *
     * @param taskName 要恢复的任务名称
     * @return 如果任务存在并成功恢复返回true，否则返回false
     */
    public boolean resumeTask(String taskName) {
        // 实际实现取决于任务如何存储和检索
        // 这里是一个简化版本
        logger.info("恢复任务: {}", taskName);
        return true;
    }

    /**
     * 更新任务调度配置
     *
     * @param taskName       要更新的任务名称
     * @param cronExpression 新的cron表达式（为null表示保持当前设置）
     * @param fixedRate      新的固定执行频率（毫秒，<=0表示禁用）
     * @param fixedDelay     新的固定延迟时间（毫秒，<=0表示禁用）
     * @return 如果任务存在并成功更新返回true，否则返回false
     */
    public boolean updateTaskSchedule(String taskName, String cronExpression, long fixedRate, long fixedDelay) {
        // 实际实现取决于任务如何存储和检索
        // 这里是一个简化版本
        logger.info("更新任务调度配置: {}, cron={}, 固定频率={}毫秒, 固定延迟={}毫秒",
                taskName, cronExpression, fixedRate, fixedDelay);
        return false; // 简化实现，实际应该返回更新结果
    }
    
    /**
     * 执行任务
     * @param taskInfo 任务信息
     */
    public void execute(TaskDomain taskInfo) {
        if (taskInfo == null) {
            logger.warn("任务信息不能为null");
            return;
        }
        
        String taskName = taskInfo.getName();
        logger.info("开始执行任务: {}", taskName);
        
        try {
            // 这里可以添加任务执行前的逻辑
            logger.debug("任务 [{}] 开始执行", taskName);
            
            // 获取任务的实际执行逻辑
            Runnable taskRunnable = createTaskRunnable(taskInfo);
            
            // 执行任务
            taskRunnable.run();
            
            logger.info("任务 [{}] 执行完成", taskName);
        } catch (Exception e) {
            logger.error("任务 [" + taskName + "] 执行失败", e);
        }
    }
    
    /**
     * 关闭任务执行器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭分布式任务执行器...");
        
        // 取消所有任务
        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();
        
        // 清理资源
        taskExecutions.clear();
        
        logger.info("分布式任务执行器已关闭");
    }

    /**
     * 内部类，用于跟踪任务执行状态
     */
    private static class TaskExecution {
        private final TaskDomain taskInfo;  // 任务信息
        private final long startTime;              // 开始时间
        private final Thread executionThread;      // 执行线程
        private final AtomicBoolean running = new AtomicBoolean(true);  // 运行状态

        public TaskExecution(TaskDomain taskInfo) {
            this.taskInfo = taskInfo;
            this.startTime = System.currentTimeMillis();
            this.executionThread = Thread.currentThread();
        }

        /**
         * 检查任务是否正在运行
         */
        public boolean isRunning() {
            return running.get();
        }

    }
}
