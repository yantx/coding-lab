package com.lab.schedule.core.executor;

import com.lab.schedule.core.TaskRegistry;
import com.lab.schedule.core.lock.DistributedLockService;
import com.lab.schedule.core.model.TaskDomain;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 有序任务执行器
 */
public class OrderedTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(OrderedTaskExecutor.class);
    private static final String TASK_QUEUE_PREFIX = "task:queue:";
    private static final String TASK_LOCK_PREFIX = "task:lock:";
    private static final long LOCK_WAIT_TIME = 10; // 秒
    private static final long LOCK_LEASE_TIME = 30; // 秒

    private final RedissonClient redissonClient;
    private final DistributedTaskExecutor distributedTaskExecutor;
    private final DistributedLockService lockService;
    private final TaskRegistry taskRegistry;
    private final ExecutorService executorService;

    public OrderedTaskExecutor(RedissonClient redissonClient,
                               DistributedLockService lockService,
                               TaskRegistry taskRegistry,
                               DistributedTaskExecutor distributedTaskExecutor) {  // 添加分布式任务执行器
        this.redissonClient = redissonClient;
        this.lockService = lockService;
        this.taskRegistry = taskRegistry;
        this.distributedTaskExecutor = distributedTaskExecutor;  // 保存引用
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * 提交有序任务
     */
    public void submitOrderedTask(TaskDomain task) {
        String group = task.getGroup();

        // 默认分组且为异步任务，直接异步执行
        if ("default".equals(group) && task.isAsync()) {
            executorService.execute(() -> distributedTaskExecutor.execute(task));
            return;
        }

        // 其他情况按顺序执行
        String lockKey = TASK_LOCK_PREFIX + group;
        String queueKey = TASK_QUEUE_PREFIX + group;

        // 使用分布式锁保证同一时间只有一个线程操作任务队列
        boolean locked = false;
        try {
            locked = lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (locked) {
                RScoredSortedSet<String> taskQueue = redissonClient.getScoredSortedSet(queueKey);

                // 添加任务到有序集合，使用order作为分数
                taskQueue.add(task.getOrder(), task.getName());
                log.debug("Task {} added to queue {} with order {}", task.getName(), queueKey, task.getOrder());

                // 获取当前应该执行的任务
                Collection<String> tasksToExecute = taskQueue.valueRange(0, true, task.getOrder(), true);
                if (!tasksToExecute.isEmpty() && tasksToExecute.iterator().next().equals(task.getName())) {
                    // 使用分布式任务执行器执行任务
                    distributedTaskExecutor.execute(task);
                    // 从队列中移除已完成任务
                    taskQueue.remove(task.getName());
                    log.debug("Task {} executed and removed from queue", task.getName());

                    // 检查并执行下一个任务
                    processNextTask(group, taskQueue);
                }
            } else {
                log.warn("Failed to acquire lock for task group: {}", group);
            }
        } catch (Exception e) {
            log.error("Ordered task submission failed: {}", task.getName(), e);
        } finally {
            if (locked) {
                lockService.unlock(lockKey);
            }
        }
    }

    private void processNextTask(String group, RScoredSortedSet<String> taskQueue) {
        // 获取并执行下一个任务
        String nextTaskName = taskQueue.first();
        if (nextTaskName != null) {
            TaskDomain nextTask = taskRegistry.getTask(nextTaskName);
            if (nextTask != null) {
                log.debug("Processing next task in group {}: {}", group, nextTaskName);
                distributedTaskExecutor.execute(nextTask);
                taskQueue.remove(nextTaskName);
            }
        }
    }

    private void safeExecute(TaskDomain task) {
        try {
            log.info("Executing task: {} in group: {}", task.getName(), task.getGroup());
            task.execute();
        } catch (Exception e) {
            log.error("Task execution failed: {}", task.getName(), e);
            // 这里可以添加重试逻辑
        }
    }
}