package com.lab.schedule.core;

import com.lab.schedule.config.SchedulerProperties;
import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.model.TaskDomain;
import com.lab.schedule.core.model.TaskGroup;
import com.lab.schedule.core.registry.TaskRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * 分布式任务注册中心
 * 负责管理所有定时任务的注册、调度和执行状态跟踪
 */
public class TaskRegistry implements ApplicationContextAware, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(TaskRegistry.class);

    private final Map<String, TaskGroup> taskGroups = new ConcurrentHashMap<>();
    private final Map<String, TaskDomain> taskInfoMap = new ConcurrentHashMap<>();
    private final ScheduledTaskRegistrar taskRegistrar = new ScheduledTaskRegistrar();

    private final DistributedTaskExecutor distributedTaskExecutor;
    private final TaskRegistryService taskRegistryService;
    private final SchedulerProperties schedulerProperties;
    private ApplicationContext applicationContext;

    /**
     * 构造函数
     *
     * @param distributedTaskExecutor 分布式任务执行器
     * @param taskRegistryService     任务注册服务
     * @param schedulerProperties     调度器配置
     */
    public TaskRegistry(
            DistributedTaskExecutor distributedTaskExecutor,
            TaskRegistryService taskRegistryService,
            SchedulerProperties schedulerProperties) {
        this.distributedTaskExecutor = distributedTaskExecutor;
        this.taskRegistryService = taskRegistryService;
        this.schedulerProperties = schedulerProperties;
        logger.info("任务注册中心初始化完成");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

        // 使用动态线程池初始化任务调度器
        int corePoolSize = schedulerProperties.getThreadPoolSize();
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(corePoolSize));
        logger.info("任务注册中心已初始化，线程池大小: {}", corePoolSize);
    }

    /**
     * 注册任务
     * @param task 任务信息
     */
    public void registerTask(TaskDomain task) {
        if (task == null || task.getName() == null) {
            throw new IllegalArgumentException("Task and task name cannot be null");
        }
        
        // 检查任务是否已存在
        if (taskInfoMap.containsKey(task.getName())) {
            logger.warn("任务 [{}] 已存在，跳过重复注册", task.getName());
            return;
        }
        
        taskInfoMap.put(task.getName(), task);
        
        // 添加到任务组
        String groupName = task.getGroup() != null ? task.getGroup() : "default";
        TaskGroup group = taskGroups.computeIfAbsent(groupName, TaskGroup::new);
        group.addTask(task);
        
        logger.info("Registered task: {} in group: {}", task.getName(), groupName);
    }
    
    /**
     * 获取所有任务组
     * @return 任务组集合
     */
    public Collection<TaskGroup> getTaskGroups() {
        return taskGroups.values();
    }

    /**
     * 立即触发任务执行
     *
     * @param taskName 要触发的任务名称
     * @return 如果任务存在并成功触发返回true，否则返回false
     */
    public boolean triggerTask(String taskName) {
        logger.info("准备触发任务: {}", taskName);
        TaskDomain taskInfo = taskInfoMap.get(taskName);
        if (taskInfo == null) {
            logger.warn("触发任务失败，未找到任务: {}", taskName);
            return false;
        }

        try {
            // 创建任务Runnable并直接执行
            Runnable taskRunnable = distributedTaskExecutor.createTaskRunnable(taskInfo);
            taskRunnable.run();
            logger.info("任务触发成功: {}", taskName);
            return true;
        } catch (Exception e) {
            logger.error("任务触发失败: " + taskName, e);
            return false;
        }
    }

    /**
     * 获取所有任务信息
     *
     * @return 任务信息集合
     */
    public Collection<TaskDomain> getTaskInfos() {
        return new ArrayList<>(taskInfoMap.values());
    }

    /**
     * 根据任务名称获取任务信息
     *
     * @param name 任务名称
     * @return 任务信息，如果不存在则返回null
     */
    public TaskDomain getTask(String name) {
        return taskInfoMap.get(name);
    }

    /**
     * 获取所有任务的基本信息
     *
     * @return 任务基本信息列表
     */
    public List<TaskDomain> getAllTasks() {
        return new ArrayList<>(taskInfoMap.values());
    }

    /**
     * 取消任务
     *
     * @param taskName 任务名称
     * @return 是否取消成功
     */
    public boolean cancelTask(String taskName) {
        TaskDomain taskInfo = taskInfoMap.get(taskName);
        if (taskInfo != null) {
            distributedTaskExecutor.cancelTask(taskName);
            taskRegistryService.unregisterTask(taskName);
            logger.info("已取消任务: {}", taskName);
            return true;
        }
        logger.warn("取消任务失败，未找到任务: {}", taskName);
        return false;
    }

    /**
     * 暂停任务
     *
     * @param taskName 任务名称
     * @return 是否暂停成功
     */
    public boolean pauseTask(String taskName) {
        TaskDomain taskInfo = taskInfoMap.get(taskName);
        if (taskInfo != null) {
            distributedTaskExecutor.pauseTask(taskName);
            logger.info("已暂停任务: {}", taskName);
            return true;
        }
        logger.warn("暂停任务失败，未找到任务: {}", taskName);
        return false;
    }

    /**
     * 恢复任务
     *
     * @param taskName 任务名称
     * @return 是否恢复成功
     */
    public boolean resumeTask(String taskName) {
        TaskDomain taskInfo = taskInfoMap.get(taskName);
        if (taskInfo != null) {
            distributedTaskExecutor.resumeTask(taskInfo);
            logger.info("已恢复任务: {}", taskName);
            return true;
        }
        logger.warn("恢复任务失败，未找到任务: {}", taskName);
        return false;
    }

    /**
     * 获取任务注册器
     *
     * @return 任务注册器实例
     */
    public ScheduledTaskRegistrar getTaskRegistrar() {
        return taskRegistrar;
    }

    /**
     * 获取所有已注册的定时任务信息
     *
     * @return 包含所有定时任务信息的Map，key为任务名称，value为任务信息
     */
    public Map<String, Object> getScheduledTasksInfo() {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, TaskDomain> entry : taskInfoMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        return result;
    }

    @Override
    public void destroy() {
        // 关闭时取消所有任务
        for (String taskName : taskInfoMap.keySet()) {
            cancelTask(taskName);
        }
        taskRegistrar.destroy();
        logger.info("任务注册中心已关闭");
    }
}
