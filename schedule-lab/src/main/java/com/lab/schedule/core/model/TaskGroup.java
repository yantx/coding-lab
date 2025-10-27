package com.lab.schedule.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 任务组信息
 */
public class TaskGroup {
    private static final Logger logger = LoggerFactory.getLogger(TaskGroup.class);
    
    private final String name;
    private final List<TaskDomain> tasks = new ArrayList<>();
    
    public TaskGroup(String name) {
        this.name = name;
    }

    /**
     * 添加任务到该组，并按照order值排序
     * @param task 要添加的任务
     */
    public void addTask(TaskDomain task) {
        tasks.add(task);
        // 按照order值排序
        tasks.sort(Comparator.comparingInt(TaskDomain::getOrder));
        logger.debug("任务组[{}] 添加任务: {}, order={}", name, task.getName(), task.getOrder());
    }
    
    /**
     * 获取任务组名
     * @return 任务组名
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取该组中的任务列表
     * @return 去重后的任务列表
     */
    public List<TaskDomain> getTasks() {
        // 使用 LinkedHashSet 去重并保持插入顺序
        List<TaskDomain> uniqueTasks = new ArrayList<>(new LinkedHashSet<>(tasks));
        // 按照order值排序
        uniqueTasks.sort(Comparator.comparingInt(TaskDomain::getOrder));
        return uniqueTasks;
    }
    
    /**
     * 获取该组中的任务数量
     * @return 任务数量
     */
    public int size() {
        return tasks.size();
    }
    
    /**
     * 检查该组是否为空
     * @return 如果该组为空返回true，否则返回false
     */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
