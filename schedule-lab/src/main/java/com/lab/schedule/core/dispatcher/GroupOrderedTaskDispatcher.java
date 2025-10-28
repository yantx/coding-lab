package com.lab.schedule.core.dispatcher;

import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 该实现确保同一分组的任务按顺序在同一个单线程执行器中串行执行；
 * 若担心分组数无限增长，可再加 LRU/弱引用或后台清理空闲执行器的逻辑；
 * RedissonShutdownException 的问题应在分布式锁释放处增加 Redisson 状态判断或捕获处理，避免在 shutdown 阶段尝试操作已关闭的 Redisson 客户端
 */
public class GroupOrderedTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GroupOrderedTaskDispatcher.class);

    private static final String DEFAULT_GROUP = "default";

    private final DistributedTaskExecutor distributedTaskExecutor;

    // 每个分组一个单线程执行器，保证组内任务串行执行
    private final ConcurrentMap<String, ExecutorService> groupExecutors = new ConcurrentHashMap<>();

    public GroupOrderedTaskDispatcher(DistributedTaskExecutor distributedTaskExecutor) {
        this.distributedTaskExecutor = distributedTaskExecutor;
    }

    /**
     * 分发任务：默认组走共享执行器，非默认组走各自单线程执行器（串行）
     */
    public void dispatch(TaskDomain task) {
        String group = task.getGroup();
        if (group == null || DEFAULT_GROUP.equals(group)) {
            // 保持原有行为
            distributedTaskExecutor.execute(task);
            return;
        }

        ExecutorService exec = groupExecutors.computeIfAbsent(group, g -> {
            ThreadFactory tf = new NamedThreadFactory("group-" + sanitizeThreadName(g));
            return Executors.newSingleThreadExecutor(tf);
        });

        exec.submit(createRunnable(task));
    }

    private Runnable createRunnable(TaskDomain task) {
        return () -> {
            try {
                // 这里调用已有的分布式执行器逻辑（包含获取/释放分布式锁等）
                distributedTaskExecutor.executeInternal(task, 60);
            } catch (Throwable t) {
                log.error("任务执行异常, task=" + task.getName(), t);
            }
        };
    }

    /**
     * 应用关闭时务必调用，安全关闭所有分组执行器
     */
    public void shutdown() {
        // 先关闭默认执行器由调用方负责（若是外部传入）
        groupExecutors.forEach((k, ex) -> {
            try {
                ex.shutdown();
            } catch (Exception e) {
                log.warn("关闭分组执行器失败: " + k, e);
            }
        });

        // 等待小段时间再强制停止
        groupExecutors.forEach((k, ex) -> {
            try {
                if (!ex.awaitTermination(2, TimeUnit.SECONDS)) {
                    ex.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ex.shutdownNow();
            }
        });
    }

    private String sanitizeThreadName(String name) {
        return name.replaceAll("[^0-9A-Za-z\\-_.]", "_");
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger idx = new AtomicInteger(0);

        NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
