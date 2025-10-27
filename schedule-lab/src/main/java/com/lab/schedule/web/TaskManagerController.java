package com.lab.schedule.web;

import com.lab.schedule.annotation.ScheduledTask;
import com.lab.schedule.core.TaskRegistry;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 定时任务管理控制器
 */
@RestController
@RequestMapping("/api/tasks1")
public class TaskManagerController {
    private static final Logger logger = LoggerFactory.getLogger(TaskManagerController.class);

    @Autowired
    private TaskRegistry taskRegistry;
    
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取所有已注册的任务
     */
    @GetMapping
    public List<Map<String, Object>> getAllTasks() {
        logger.info("获取所有定时任务");
        List<Map<String, Object>> tasks = taskRegistry.getAllTasks().stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());
        logger.debug("获取到{}个定时任务", tasks.size());
        return tasks;
    }

    /**
     * 根据任务名称获取任务详情
     * 
     * @param taskName 任务名称
     * @return 任务详情
     */
    @GetMapping("/{taskName}")
    public Map<String, Object> getTask(@PathVariable String taskName) {
        logger.info("获取任务详情: {}", taskName);
        TaskDomain task = taskRegistry.getTask(taskName);
        if (task == null) {
            logger.warn("未找到任务: {}", taskName);
            throw new RuntimeException("未找到任务: " + taskName);
        }
        return convertToMap(task);
    }

    /**
     * 手动触发任务执行
     * 
     * @param taskName 任务名称
     * @return 操作结果
     */
    @PostMapping("/{taskName}/trigger")
    public Map<String, Object> triggerTask(@PathVariable String taskName) {
        logger.info("手动触发任务: {}", taskName);
        try {
            taskRegistry.triggerTask(taskName);
            logger.info("任务触发成功: {}", taskName);
            return createSuccessResult("任务触发成功: " + taskName);
        } catch (Exception e) {
            logger.error("任务触发失败: " + taskName, e);
            return createErrorResult("任务触发失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有任务分组
     */
    @GetMapping("/groups")
    public Map<String, Object> getTaskGroups() {
        logger.info("获取所有任务分组");
        Map<String, Object> result = new HashMap<>();
        Map<String, List<String>> groups = new HashMap<>();
        
        taskRegistry.getTaskGroups().forEach(group -> {
            List<String> taskNames = group.getTasks().stream()
                    .map(TaskDomain::getName)
                    .collect(Collectors.toList());
            groups.put(group.getName(), taskNames);
            logger.debug("分组[{}]包含{}个任务", group.getName(), taskNames.size());
        });
        
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", groups);
        return result;
    }

    /**
     * 根据分组名称获取任务列表
     * 
     * @param groupName 分组名称
     * @return 任务列表
     */
    @GetMapping("/group/{groupName}")
    public Map<String, Object> getTasksByGroup(@PathVariable String groupName) {
        logger.info("获取分组[{}]的任务列表", groupName);
        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> tasks = taskRegistry.getTaskGroups().stream()
                .filter(g -> g.getName().equals(groupName))
                .flatMap(g -> g.getTasks().stream())
                .map(this::convertToMap)
                .collect(Collectors.toList());
                
        if (tasks.isEmpty()) {
            logger.warn("未找到分组: {}", groupName);
            result.put("code", 404);
            result.put("message", "未找到分组: " + groupName);
            result.put("data", null);
        } else {
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", tasks);
            logger.debug("分组[{}]包含{}个任务", groupName, tasks.size());
        }
        
        return result;
    }

    /**
     * 获取当前正在运行的任务
     */
    @GetMapping("/running")
    public Map<String, Object> getRunningTasks() {
        logger.info("获取所有正在运行的任务");
        List<Map<String, Object>> runningTasks = taskRegistry.getAllTasks().stream()
                .filter(TaskDomain::isRunning)
                .map(this::convertToMap)
                .collect(Collectors.toList());
                
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", runningTasks);
        logger.info("当前有{}个任务正在运行", runningTasks.size());
        return result;
    }
    
    /**
     * 转换任务信息为Map
     */
    private Map<String, Object> convertToMap(TaskDomain task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", task.getName());
        map.put("group", task.getGroup());
        map.put("running", task.isRunning());
        map.put("lastExecutionTime", task.getLastExecutionTime());
        map.put("nextExecutionTime", task.getNextExecutionTime());
        return map;
    }
    
    /**
     * 获取所有被@ScheduledTask注解的方法信息
     * @return 包含所有被注解方法信息的列表
     */
    @GetMapping("/declared")
    public List<Map<String, Object>> listDeclaredTasks() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 获取所有bean名称
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();
            
            // 检查类的方法上的注解
            for (Method method : beanClass.getDeclaredMethods()) {
                ScheduledTask annotation = method.getAnnotation(ScheduledTask.class);
                if (annotation != null) {
                    Map<String, Object> methodInfo = new HashMap<>();
                    methodInfo.put("beanName", beanName);
                    methodInfo.put("className", beanClass.getName());
                    methodInfo.put("methodName", method.getName());
                    methodInfo.put("taskName", annotation.name());
                    methodInfo.put("description", annotation.description());
                    methodInfo.put("group", annotation.group());
                    methodInfo.put("cron", annotation.cron());
                    methodInfo.put("fixedRate", annotation.fixedRate());
                    methodInfo.put("fixedDelay", annotation.fixedDelay());
                    methodInfo.put("initialDelay", annotation.initialDelay());
                    
                    // 检查是否已注册
                    boolean isRegistered = taskRegistry.getTask(annotation.name()) != null;
                    methodInfo.put("isRegistered", isRegistered);
                    
                    result.add(methodInfo);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", null);
        return result;
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        result.put("data", null);
        return result;
    }
}
