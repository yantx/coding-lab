package com.lab.schedule.core.manager;

import com.lab.schedule.core.dispatcher.GroupOrderedTaskDispatcher;
import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.DistributedTaskScheduler;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一任务管理器：增加按分组顺序分发逻辑，提交执行时优先走 GroupOrderedTaskDispatcher。
 */
public class DistributedTaskManager {
    private static final Logger logger = LoggerFactory.getLogger(DistributedTaskManager.class);

    private final DistributedTaskScheduler scheduler;
    private final DistributedTaskExecutor executor;
    private final GroupOrderedTaskDispatcher groupDispatcher;
    private final Map<String, TaskDomain> registry = new ConcurrentHashMap<>();

    public DistributedTaskManager(DistributedTaskScheduler scheduler, DistributedTaskExecutor executor) {
        this.groupDispatcher = new GroupOrderedTaskDispatcher(executor);
        this.scheduler = scheduler;
        this.executor = executor;
    }

    public void registerTask(TaskDomain task) {
        if (task == null) return;
        registry.put(task.getName(), task);
        scheduler.scheduleTask(task);
        logger.info("已注册任务: {}", task.getName());
    }

    public void unregisterTask(String name) {
        registry.remove(name);
        scheduler.cancelTask(name);
        logger.info("已注销任务: {}", name);
    }

    /**
     * 手动触发（立即尝试执行）——也遵循组内有序策略
     */
    public boolean triggerTask(String name) {
        TaskDomain task = registry.get(name);
        if (task == null) {
            logger.warn("触发失败，未找到任务: {}", name);
            return false;
        }
        submitForExecution(task);
        logger.info("手动触发任务: {}", name);
        return true;
    }

    /**
     * Scheduler / 外部调用请通过此方法提交执行，内部会根据分组与 isAsync 决定直接执行或入组队列。
     */
    public void submitForExecution(TaskDomain task) {
        groupDispatcher.dispatch(task);
    }

    public void resumeTask(String name) {
        TaskDomain task = registry.get(name);
        if (task == null) {
            logger.warn("恢复失败，未找到任务: {}", name);
            return;
        }
        scheduler.scheduleTask(task);
        logger.info("已恢复任务: {}", name);
    }

    public void shutdown() {
        groupDispatcher.shutdown();
    }
}
