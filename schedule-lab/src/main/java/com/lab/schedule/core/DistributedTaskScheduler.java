package com.lab.schedule.core;

import com.lab.schedule.annotation.ScheduledTask;
import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.executor.OrderedTaskExecutor;
import com.lab.schedule.core.executor.TaskGroupExecutorManager;
import com.lab.schedule.core.model.TaskDomain;
import com.lab.schedule.core.model.TaskGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * 分布式任务调度器
 * 负责管理分布式环境下定时任务的生命周期，包括任务的注册、调度和执行
 * 支持SpEL表达式和属性占位符
 */
public class DistributedTaskScheduler implements ApplicationContextAware, SmartLifecycle, DisposableBean, ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskScheduler.class);
    
    private final TaskRegistry taskRegistry;
    private final DistributedTaskExecutor distributedTaskExecutor;
    private final OrderedTaskExecutor orderedTaskExecutor;

    private final TaskGroupExecutorManager taskGroupExecutorManager;
    private final Map<String, TaskDomain> scheduledTasks = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private ApplicationContext applicationContext;
    private BeanFactory beanFactory;

    public DistributedTaskScheduler(TaskRegistry taskRegistry,
                                    DistributedTaskExecutor distributedTaskExecutor,
                                    OrderedTaskExecutor orderedTaskExecutor) {
        this.taskRegistry = taskRegistry;
        this.distributedTaskExecutor = distributedTaskExecutor;
        this.orderedTaskExecutor = orderedTaskExecutor;
        this.taskGroupExecutorManager = new TaskGroupExecutorManager();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (this.running) {
            log.info("Application started, initializing scheduled tasks...");
            
            // 注册所有任务
            registerAllTasks();

            List<TaskDomain> scheduledTasks = taskRegistry.getAllTasks().stream()
                    .sorted(Comparator.comparing(TaskDomain::getOrder))
                    .collect(Collectors.toList());
            // 启动所有已启用的任务
            for (TaskDomain task : scheduledTasks) {
                log.info("Task [{}] is enabled, scheduling...", task.getName());
                if (task.isEnabled()) {
                    this.scheduledTasks.put(task.getName(), task);
                    scheduleTask(task.getName());
                }
            }
            
            log.info("Scheduled tasks initialized. Total tasks: {}", scheduledTasks.size());
        }
    }

    @Override
    public void start() {
        if (this.running) {
            return;
        }
        log.info("Starting DistributedTaskScheduler...");
        this.running = true;
        // 任务注册和启动现在在 run 方法中完成
    }

    @Override
    public void stop() {
        if (!this.running) {
            return;
        }

        log.info("Stopping DistributedTaskScheduler...");
        this.running = false;

        // 取消所有任务
        for (TaskDomain task : scheduledTasks.values()) {
            if (task.getScheduledFuture() != null) {
                task.getScheduledFuture().cancel(false);
            }
        }

        scheduledTasks.clear();
        log.info("DistributedTaskScheduler stopped");
    }

    @Override
    public void destroy() {
        stop();
        this.taskGroupExecutorManager.shutdown();
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // 最后启动，最先关闭
    }

    /**
     * 获取所有任务组
     * @return 任务组集合
     */
    public Collection<TaskGroup> getTaskGroups() {
        return taskRegistry.getTaskGroups();
    }

    /**
     * 注册所有任务
     */
    private void registerAllTasks() {
        // 从Spring上下文中获取所有带有@ScheduledTask注解的方法
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);
        for (Object bean : beans.values()) {
            Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());
            for (Method method : methods) {
                ScheduledTask annotation = AnnotationUtils.findAnnotation(method, ScheduledTask.class);
                if (annotation != null) {
                    registerTask(bean, method, annotation);
                }
            }
        }
    }

    /**
     * 解析SpEL表达式或属性占位符
     */
    private String resolveExpression(String expression, Object target, Method method) {
        if (!StringUtils.hasText(expression)) {
            return expression;
        }
        
        // 如果是属性占位符 ${...}
        if (expression.startsWith("$") && expression.contains("{") && expression.endsWith("}")) {
            return applicationContext.getEnvironment().resolvePlaceholders(expression);
        }
        
        // 如果是SpEL表达式 #{...}
        if (expression.startsWith("#{") && expression.endsWith("}")) {
            try {
                StandardEvaluationContext context = new StandardEvaluationContext();
                context.setBeanResolver(new BeanFactoryResolver(beanFactory));
                context.setVariable("target", target);
                context.setVariable("method", method);
                
                ExpressionParser parser = new SpelExpressionParser();
                Expression exp = parser.parseExpression(expression, new TemplateParserContext());
                Object value = exp.getValue(context);
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                log.warn("Failed to evaluate SpEL expression: " + expression, e);
                return expression;
            }
        }
        
        return expression;
    }
    
    /**
     * 注册单个任务
     */
    private void registerTask(Object bean, Method method, ScheduledTask annotation) {
        try {
            // 解析名称
            String taskName = StringUtils.hasText(annotation.name()) ? 
                resolveExpression(annotation.name(), bean, method) : 
                bean.getClass().getSimpleName() + "#" + method.getName();
            
            // 解析分组
            String group = resolveExpression(annotation.group(), bean, method);
            if (!StringUtils.hasText(group)) {
                group = "default";
            }
            
            // 创建任务信息
            TaskDomain taskInfo = new TaskDomain();
            taskInfo.setName(taskName);
            taskInfo.setGroup(group);
            
            // 解析cron表达式
            if (StringUtils.hasText(annotation.cron())) {
                taskInfo.setCron(resolveExpression(annotation.cron(), bean, method));
            }
            
            // 解析fixedRate
            if (StringUtils.hasText(annotation.fixedRateString())) {
                String fixedRateStr = resolveExpression(annotation.fixedRateString(), bean, method);
                try {
                    taskInfo.setFixedRate(Long.parseLong(fixedRateStr));
                } catch (NumberFormatException e) {
                    log.warn("Invalid fixedRate value: {}", fixedRateStr);
                    taskInfo.setFixedRate(-1);
                }
            } else if (annotation.fixedRate() > 0) {
                taskInfo.setFixedRate(annotation.fixedRate());
            }
            
            // 解析fixedDelay
            if (StringUtils.hasText(annotation.fixedDelayString())) {
                String fixedDelayStr = resolveExpression(annotation.fixedDelayString(), bean, method);
                try {
                    taskInfo.setFixedDelay(Long.parseLong(fixedDelayStr));
                } catch (NumberFormatException e) {
                    log.warn("Invalid fixedDelay value: {}", fixedDelayStr);
                    taskInfo.setFixedDelay(-1);
                }
            } else if (annotation.fixedDelay() > 0) {
                taskInfo.setFixedDelay(annotation.fixedDelay());
            }
            
            // 解析initialDelay
            if (StringUtils.hasText(annotation.initialDelayString())) {
                String initialDelayStr = resolveExpression(annotation.initialDelayString(), bean, method);
                try {
                    taskInfo.setInitialDelay(Long.parseLong(initialDelayStr));
                } catch (NumberFormatException e) {
                    log.warn("Invalid initialDelay value: {}", initialDelayStr);
                    taskInfo.setInitialDelay(0);
                }
            } else {
                taskInfo.setInitialDelay(annotation.initialDelay());
            }
            
            // 设置其他属性
            taskInfo.setAsync(annotation.async());
            taskInfo.setDescription(annotation.description());
            taskInfo.setOrder(annotation.order());
            taskInfo.setEnabled(annotation.enabled());
            taskInfo.setMaxRetries(annotation.maxRetries());
            taskInfo.setRetryDelay(annotation.retryDelay());
            taskInfo.setTimeout(annotation.timeout());
            taskInfo.setDistributed(annotation.distributed());

            taskInfo.setTargetBean(bean);
            taskInfo.setTargetMethod(method);

            // 注册任务
            taskRegistry.registerTask(taskInfo);
            log.info("Registered task: {} in group: {}", taskName, group);

        } catch (Exception e) {
            log.error("Failed to register task: " + method.getName(), e);
            throw new RuntimeException("Failed to register scheduled task", e);
        }
    }

    /**
     * 调度任务
     * @param taskName 任务名称
     */
    public void scheduleTask(String taskName) {
        TaskDomain taskInfo = taskRegistry.getTask(taskName);
        if (taskInfo == null) {
            log.warn("Task not found: {}", taskName);
            return;
        }

        // 取消已存在的任务
        if (scheduledTasks.containsKey(taskName)) {
            TaskDomain existingTask = scheduledTasks.get(taskName);
            if (existingTask != null && existingTask.getScheduledFuture() != null) {
                existingTask.getScheduledFuture().cancel(true);
                scheduledTasks.remove(taskName);
                log.info("Cancelled existing task: {}", taskName);
            }
        }

        // 创建任务包装器
        Runnable task = () -> {
            // 使用 OrderedTaskExecutor 来执行任务
            // 它内部会调用 DistributedTaskExecutor 来确保分布式执行
            orderedTaskExecutor.submitOrderedTask(taskInfo);
        };

        // 获取任务组执行器
        String group = taskInfo.getGroup();
        ThreadPoolTaskScheduler executor = taskGroupExecutorManager.getOrCreateExecutor(group);

        // 调度任务
        ScheduledFuture<?> future = null;
        try {
            if (taskInfo.isCronBased()) {
                future = executor.schedule(task, new CronTrigger(taskInfo.getCron()));
                log.info("Scheduled cron task: {}, group: {}, cron: {}",
                        taskName, group, taskInfo.getCron());
            } else if (taskInfo.isFixedRate()) {
                future = executor.scheduleAtFixedRate(
                        task,
                        Instant.now().plusMillis(taskInfo.getInitialDelay() > 0 ? taskInfo.getInitialDelay() : 0),
                        Duration.ofMillis(taskInfo.getFixedRate())
                );
                log.info("Scheduled fixed rate task: {}, group: {}, initialDelay: {}ms, rate: {}ms",
                        taskName, group, taskInfo.getInitialDelay(), taskInfo.getFixedRate());
            } else if (taskInfo.isFixedDelay()) {
                future = executor.scheduleWithFixedDelay(
                        task,
                        Instant.now().plusMillis(taskInfo.getInitialDelay() > 0 ? taskInfo.getInitialDelay() : 0),
                        Duration.ofMillis(taskInfo.getFixedDelay())
                );
                log.info("Scheduled fixed delay task: {}, group: {}, initialDelay: {}ms, delay: {}ms",
                        taskName, group, taskInfo.getInitialDelay(), taskInfo.getFixedDelay());
            }

            // 保存调度信息
            if (future != null) {
                taskInfo.setScheduledFuture(future);
                scheduledTasks.put(taskName, taskInfo);
            }
        } catch (Exception e) {
            log.error("Failed to schedule task: " + taskName, e);
        }
    }
//    public void scheduleTask(String taskName) {
//        TaskDomain taskInfo = taskRegistry.getTask(taskName);
//        if (taskInfo == null) {
//            log.warn("Task not found: {}", taskName);
//            return;
//        }
//
//        // 如果任务已存在，先取消
//        if (scheduledTasks.containsKey(taskName)) {
//            TaskDomain existingTask = scheduledTasks.get(taskName);
//            if (existingTask != null && existingTask.getScheduledFuture() != null) {
//                existingTask.getScheduledFuture().cancel(true);
//                scheduledTasks.remove(taskName);
//                log.info("Cancelled existing task: {}", taskName);
//            }
//        }
//
//        // 获取任务组执行器
//        String group = taskInfo.getGroup();
//        ThreadPoolTaskScheduler executor = taskGroupExecutorManager.getOrCreateExecutor(group);
//
//
//        // 创建任务
//        Runnable task = () -> {
//            try {
//                log.debug("Executing task: {} in group: {}", taskName, group);
//                distributedTaskExecutor.execute(taskInfo);
//            } catch (Exception e) {
//                log.error("Error executing task: {}", taskName, e);
//            }
//        };
//        ScheduledFuture<?> future = null;
//        // 根据任务类型进行调度
//        try {
//            if (taskInfo.isCronBased()) {
//                future = executor.schedule(task, new CronTrigger(taskInfo.getCron()));
//                log.info("Scheduled cron task: {}, group: {}, cron: {}", taskName, group, taskInfo.getCron());
//            } else if (taskInfo.isFixedRate()) {
//                future = executor.scheduleAtFixedRate(
//                    task,
//                    Instant.now().plusMillis(taskInfo.getInitialDelay() > 0 ? taskInfo.getInitialDelay() : 0),
//                    Duration.ofMillis(taskInfo.getFixedRate())
//                );
//                log.info("Scheduled fixed rate task: {}, group: {}, initialDelay: {}ms, rate: {}ms",
//                    taskName, group, taskInfo.getInitialDelay(), taskInfo.getFixedRate());
//            } else if (taskInfo.isFixedDelay()) {
//                future = executor.scheduleWithFixedDelay(
//                    task,
//                    Instant.now().plusMillis(taskInfo.getInitialDelay() > 0 ? taskInfo.getInitialDelay() : 0),
//                    Duration.ofMillis(taskInfo.getFixedDelay())
//                );
//                log.info("Scheduled fixed delay task: {}, group: {}, initialDelay: {}ms, delay: {}ms",
//                    taskName, group, taskInfo.getInitialDelay(), taskInfo.getFixedDelay());
//            }
//
//            // 保存调度信息
//            if (future != null) {
//                taskInfo.setScheduledFuture(future);
//                scheduledTasks.put(taskName, taskInfo);
//                taskGroupExecutorManager.registerTask(group, taskName, future);
//            }
//        } catch (Exception e) {
//            log.error("Failed to schedule task: " + taskName, e);
//        }
//    }

    /**
     * 获取所有已调度的任务信息
     * @return 包含所有已调度任务信息的Map，key为任务名称，value为任务信息
     */
    public Map<String, TaskDomain> getScheduledTasksInfo() {
        return new ConcurrentHashMap<>(scheduledTasks);
    }
}
