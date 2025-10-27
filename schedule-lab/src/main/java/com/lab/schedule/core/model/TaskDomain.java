package com.lab.schedule.core.model;

import com.lab.schedule.annotation.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务信息类，合并了TaskInfo和ScheduledTaskInfo的功能
 */
public class TaskDomain implements Serializable, Comparable<TaskDomain> {
    private static final long serialVersionUID = 1L;
    
    // 任务标识和配置
    private String name;                     // 任务名称
    private String group = "default";        // 任务分组
    private String cron;                     // cron表达式
    private long fixedRate = -1;             // 固定频率执行，单位毫秒
    private long fixedDelay = -1;            // 固定延迟执行，单位毫秒
    private long initialDelay = 0;           // 初始延迟时间，单位毫秒
    private boolean async = false;           // 是否异步执行
    private boolean enabled = true;          // 是否启用
    private String description = "";         // 任务描述
    private int order = 0;                   // 执行顺序
    private int maxRetries = 0;              // 最大重试次数
    private long retryDelay = 5000;          // 重试延迟时间，单位毫秒
    private long timeout = 0;                // 任务超时时间，单位毫秒，0表示不超时
    private boolean distributed = true;      // 是否分布式执行
    
    // 执行相关
    private final ScheduledTask annotation;  // 任务注解
    private Object targetBean;               // 目标Bean
    private Method targetMethod;             // 目标方法
    private ScheduledFuture<?> scheduledFuture; // 调度Future
    private TriggerTask triggerTask;         // 触发器任务
    private ScheduledTaskRegistrar taskRegistrar; // 任务注册器
    
    // 执行状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private String nodeId;                   // 执行节点ID
    private LocalDateTime lastExecutionTime; // 最后执行时间
    private LocalDateTime nextExecutionTime; // 下次执行时间
    private long executionCount;             // 执行次数
    private long errorCount;                 // 错误次数
    private String lastError;                // 最后错误信息
    
    // 执行统计
    private String threadName;               // 执行线程名
    private long lastExecutionDuration;      // 上次执行耗时(ms)
    private long maxExecutionTime;           // 最大执行时间(ms)
    private long minExecutionTime = Long.MAX_VALUE; // 最小执行时间(ms)
    private long totalExecutionTime;         // 总执行时间(ms)
    private double averageExecutionTime;     // 平均执行时间(ms)
    private volatile long lastHeartbeat;     // 最后心跳时间戳
    

    // 构造方法
    public TaskDomain() {
        this.annotation = null;
    }

    public TaskDomain(String name, String group) {
        this.name = name;
        this.group = group != null ? group : "default";
        this.annotation = null;
    }

    public TaskDomain(String name, String group, ScheduledTask annotation) {
        this.name = name;
        this.group = group != null ? group : "default";
        this.annotation = annotation;
    }

    // 任务操作方法
    public void updateExecutionStats(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        this.lastExecutionDuration = duration;
        this.totalExecutionTime += duration;
        this.maxExecutionTime = Math.max(this.maxExecutionTime, duration);
        this.minExecutionTime = Math.min(this.minExecutionTime, duration);
        this.averageExecutionTime = (double) this.totalExecutionTime / getExecutionCount();
        
        log("DEBUG", String.format("Task %s executed in %d ms (avg: %.2f ms, min: %d ms, max: %d ms)",
            name, duration, averageExecutionTime,
            minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime,
            maxExecutionTime));
    }
    
    public synchronized void pause() {
        if (!paused.get() && scheduledFuture != null) {
            scheduledFuture.cancel(false);
            paused.set(true);
            log("INFO", "Paused task: " + name);
        }
    }
    
    public synchronized void resume() {
        if (paused.get()) {
            paused.set(false);
            log("INFO", "Resumed task: " + name);
        }
    }
    
    public synchronized void cancel() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
            log("INFO", "Cancelled task: " + name);
        }
    }
    
    public void markStarted() {
        this.running.set(true);
        this.lastExecutionTime = LocalDateTime.now();
    }
    
    public void markCompleted() {
        this.running.set(false);
        this.executionCount++;
        this.lastError = null;
        this.lastExecutionTime = LocalDateTime.now();
    }
    
    public void markFailed(String error) {
        this.running.set(false);
        this.errorCount++;
        this.lastError = error;
    }
    
    // 辅助方法
    private void log(String level, String message) {
        String logMessage = String.format("[%s] %s: %s", 
            level, getClass().getSimpleName(), message);
        System.out.println(logMessage);
    }
    
    // 状态检查方法
    public boolean isCronBased() {
        return cron != null && !cron.isEmpty();
    }
    
    public boolean isFixedRate() {
        return fixedRate > 0;
    }
    
    public boolean isFixedDelay() {
        return fixedDelay > 0;
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isCancelled() {
        return scheduledFuture == null || scheduledFuture.isCancelled();
    }
    
    public boolean isDone() {
        return scheduledFuture != null && scheduledFuture.isDone();
    }
    
    public String getStatus() {
        if (!enabled) {
            return "DISABLED";
        } else if (paused.get()) {
            return "PAUSED";
        } else if (running.get()) {
            return "RUNNING";
        } else if (lastError != null) {
            return "ERROR";
        } else if (isCancelled()) {
            return "CANCELLED";
        } else if (isDone()) {
            return "COMPLETED";
        } else {
            return "IDLE";
        }
    }
    
    // 获取统计信息
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("executionCount", executionCount);
        stats.put("errorCount", errorCount);
        stats.put("lastExecutionTime", lastExecutionTime);
        stats.put("nextExecutionTime", nextExecutionTime);
        stats.put("lastExecutionDuration", lastExecutionDuration);
        stats.put("averageExecutionTime", String.format("%.2f ms", averageExecutionTime));
        stats.put("minExecutionTime", minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime);
        stats.put("maxExecutionTime", maxExecutionTime);
        stats.put("status", getStatus());
        stats.put("threadName", threadName != null ? threadName : "N/A");
        return stats;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("group", group);
        map.put("cron", cron);
        map.put("fixedRate", fixedRate);
        map.put("fixedDelay", fixedDelay);
        map.put("initialDelay", initialDelay);
        map.put("async", async);
        map.put("running", isRunning());
        map.put("lastExecutionTime", lastExecutionTime);
        map.put("nextExecutionTime", nextExecutionTime);
        map.put("executionCount", executionCount);
        map.put("errorCount", errorCount);
        map.put("status", getStatus());
        map.put("nodeId", nodeId);
        return map;
    }
    
    @Override
    public String toString() {
        return "ScheduledTask{" +
                "name='" + name + '\'' +
                ", group='" + group + '\'' +
                ", cron='" + cron + '\'' +
                ", fixedRate=" + fixedRate +
                ", fixedDelay=" + fixedDelay +
                ", initialDelay=" + initialDelay +
                ", async=" + async +
                ", enabled=" + enabled +
                ", description='" + description + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", lastExecutionTime=" + lastExecutionTime +
                ", nextExecutionTime=" + nextExecutionTime +
                ", executionCount=" + executionCount +
                ", errorCount=" + errorCount +
                ", lastError='" + lastError + '\'' +
                ", running=" + running.get() +
                '}';
    }
    
    @Override
    public int compareTo(TaskDomain other) {
        return Integer.compare(this.order, other.order);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDomain that = (TaskDomain) o;
        return Objects.equals(name, that.name) && 
               Objects.equals(group, that.group);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, group);
    }
    
    // Getter 和 Setter 方法
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getGroup() { return group != null ? group : "default"; }
    public void setGroup(String group) { this.group = group; }
    
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    
    public long getFixedRate() { return fixedRate; }
    public void setFixedRate(long fixedRate) { this.fixedRate = fixedRate; }
    
    public long getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(long fixedDelay) { this.fixedDelay = fixedDelay; }
    
    public long getInitialDelay() { return initialDelay; }
    public void setInitialDelay(long initialDelay) { this.initialDelay = initialDelay; }
    
    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public long getRetryDelay() { return retryDelay; }
    public void setRetryDelay(long retryDelay) { this.retryDelay = retryDelay > 0 ? retryDelay : 0; }
    
    public long getTimeout() { return timeout; }
    public void setTimeout(long timeout) { this.timeout = timeout >= 0 ? timeout : 0; }
    
    public boolean isDistributed() { return distributed; }
    public void setDistributed(boolean distributed) { this.distributed = distributed; }
    
    public ScheduledTask getAnnotation() { return annotation; }
    
    public Object getTargetBean() { return targetBean; }
    public void setTargetBean(Object targetBean) { this.targetBean = targetBean; }
    
    public Method getTargetMethod() { return targetMethod; }
    public void setTargetMethod(Method targetMethod) { this.targetMethod = targetMethod; }
    
    public ScheduledFuture<?> getScheduledFuture() { return scheduledFuture; }
    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) { this.scheduledFuture = scheduledFuture; }
    
    public TriggerTask getTriggerTask() { return triggerTask; }
    public void setTriggerTask(TriggerTask triggerTask) { this.triggerTask = triggerTask; }
    
    public ScheduledTaskRegistrar getTaskRegistrar() { return taskRegistrar; }
    public void setTaskRegistrar(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
    }
    
    /**
     * 执行任务
     * @throws Exception 执行过程中可能抛出的异常
     */
    public void execute() throws Exception {
        if (targetBean == null || targetMethod == null) {
            throw new IllegalStateException("Target bean or method is not set for task: " + name);
        }
        
        try {
            // 设置方法可访问
            targetMethod.setAccessible(true);
            
            // 执行方法
            if (targetMethod.getParameterCount() == 0) {
                targetMethod.invoke(targetBean);
            } else {
                // 如果有参数，可以在这里处理参数注入
                // 这里简单处理为不传参，实际使用时可能需要更复杂的参数解析逻辑
                targetMethod.invoke(targetBean, (Object[]) null);
            }
            
            // 更新最后执行时间
            this.lastExecutionTime = LocalDateTime.now();
            this.running.set(false);
            
        } catch (Exception e) {
            this.running.set(false);
            throw e;
        }
    }
    
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public LocalDateTime getLastExecutionTime() { return lastExecutionTime; }
    public void setLastExecutionTime(LocalDateTime lastExecutionTime) { this.lastExecutionTime = lastExecutionTime; }
    
    public LocalDateTime getNextExecutionTime() { return nextExecutionTime; }
    public void setNextExecutionTime(LocalDateTime nextExecutionTime) { this.nextExecutionTime = nextExecutionTime; }
    
    public long getExecutionCount() { return executionCount; }
    public void setExecutionCount(long executionCount) { this.executionCount = executionCount; }
    public void incrementExecutionCount() { this.executionCount++; }
    
    public long getErrorCount() { return errorCount; }
    public void setErrorCount(long errorCount) { this.errorCount = errorCount; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    public void setRunning(boolean running) { this.running.set(running); }
    
    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }
    
    public long getLastExecutionDuration() { return lastExecutionDuration; }
    public long getMaxExecutionTime() { return maxExecutionTime; }
    public long getMinExecutionTime() { return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime; }
    public double getAverageExecutionTime() { return averageExecutionTime; }
    
    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
}
