package com.lab.schedule.core.executor;

import com.lab.schedule.core.lock.DistributedLockService;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * 仅负责任务执行与锁管理
 */
public class DistributedTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DistributedTaskExecutor.class);

    private final DistributedLockService lockService;
    private final Map<String, TaskExecution> taskExecutions = new ConcurrentHashMap<>();

    public DistributedTaskExecutor(DistributedLockService lockService) {
        this.lockService = lockService;
    }

    public Runnable createTaskRunnable(final TaskDomain taskInfo) {
        return () -> executeInternal(taskInfo, 60);
    }

    public void execute(TaskDomain taskInfo) {
        executeInternal(taskInfo, 60);
    }

    private void executeInternal(TaskDomain taskInfo, long lockHoldSeconds) {
        if (taskInfo == null) {
            logger.warn("任务信息不能为null");
            return;
        }
        final String taskName = taskInfo.getName();
        final String lockKey = "schedule:task:lock:" + taskName;

        if (isTaskRunning(taskName)) {
            logger.trace("任务 {} 已在运行，跳过本次执行", taskName);
            return;
        }

        boolean locked = false;
        try {
            locked = lockService.tryLock(lockKey, 0, lockHoldSeconds, TimeUnit.SECONDS);
            if (!locked) {
                logger.trace("无法获取任务 {} 的分布式锁，跳过执行", taskName);
                return;
            }

            TaskExecution exec = new TaskExecution(taskInfo);
            taskExecutions.put(taskName, exec);

            int maxRetries = Math.max(0, taskInfo.getMaxRetries());
            long retryDelay = Math.max(0, taskInfo.getRetryDelay());
            int attempt = 0;

            while (true) {
                long startTime = System.currentTimeMillis();
                try {
                    logger.debug("开始执行任务: {} (attempt={})", taskName, attempt);
                    taskInfo.markStarted();
                    taskInfo.execute();
                    taskInfo.markCompleted();
                    taskInfo.updateExecutionStats(startTime);
                    logger.debug("任务执行完成: {} (attempt={})", taskName, attempt);
                    break; // 执行成功，退出重试循环
                } catch (Exception e) {
                    taskInfo.markFailed(e.getMessage());
                    taskInfo.updateExecutionStats(startTime);
                    logger.error("任务执行失败: {} (attempt={}), error={}", taskName, attempt, e.getMessage(), e);

                    if (attempt >= maxRetries) {
                        logger.warn("任务 {} 达到最大重试次数 {}，不再重试", taskName, maxRetries);
                        break;
                    } else {
                        attempt++;
                        logger.info("任务 {} 将在 {} ms 后重试 (nextAttempt={})", taskName, retryDelay, attempt);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.warn("重试等待被中断，停止重试：{}", taskName);
                            break;
                        }
                        // 继续下一次尝试
                    }
                }
            }

        } catch (Exception e) {
            logger.error("任务执行包装器发生错误: " + taskName, e);
        } finally {
            // 清理执行状态并释放锁
            taskExecutions.remove(taskName);
            if (locked) {
                try {
                    lockService.unlock(lockKey);
                } catch (Exception e) {
                    logger.error("释放任务 " + taskName + " 的分布式锁失败", e);
                }
            }
        }
    }

    public boolean isTaskRunning(String taskName) {
        TaskExecution execution = taskExecutions.get(taskName);
        return execution != null && execution.isRunning();
    }

    private static class TaskExecution {
        private final TaskDomain taskInfo;
        private final long startTime;
        private final AtomicBoolean running = new AtomicBoolean(true);

        public TaskExecution(TaskDomain taskInfo) {
            this.taskInfo = taskInfo;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isRunning() {
            return running.get();
        }
    }
}
