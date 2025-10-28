package com.lab.schedule.core.dispatcher;

import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.model.TaskDomain;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按分组维护单线程消费者与优先队列，保证同一分组内非异步任务按 order 升序执行。
 */
public class Task {

    private final ConcurrentMap<String, GroupWorker> workers = new ConcurrentHashMap<>();
    private final AtomicLong seqGen = new AtomicLong(0);

    public void submit(TaskDomain task, DistributedTaskExecutor executor) {
        if (task == null || executor == null) return;

        String group = task.getGroup();
        boolean isDefaultGroup = group == null || "default".equals(group);
        if (task.isAsync() || isDefaultGroup) {
            // 直接执行（不保证组内顺序）
            executor.execute(task);
            return;
        }

        GroupWorker w = workers.computeIfAbsent(group, g -> new GroupWorker(g));
        long seq = seqGen.getAndIncrement();
        w.enqueue(new TaskItem(task, executor, seq));
    }

    public void shutdown() {
        for (GroupWorker w : workers.values()) {
            w.shutdown();
        }
        workers.clear();
    }

    // ----- 内部类 -----
    private static class TaskItem {
        final TaskDomain task;
        final DistributedTaskExecutor executor;
        final long seq;

        TaskItem(TaskDomain task, DistributedTaskExecutor executor, long seq) {
            this.task = task;
            this.executor = executor;
            this.seq = seq;
        }
    }

    private static class GroupWorker {
        private final String group;
        private final PriorityBlockingQueue<TaskItem> queue;
        private final ExecutorService worker;
        private final AtomicBoolean running = new AtomicBoolean(true);

        GroupWorker(String group) {
            this.group = group;
            // Comparator: 按 order 升序，order 相同时按提交序号（seq）保证稳定性
            this.queue = new PriorityBlockingQueue<>(16, (a, b) -> {
                long ao = safeOrder(a.task);
                long bo = safeOrder(b.task);
                if (ao != bo) return Long.compare(ao, bo);
                return Long.compare(a.seq, b.seq);
            });
            this.worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "group-worker-" + group);
                t.setDaemon(true);
                return t;
            });
            start();
        }

        private static long safeOrder(TaskDomain t) {
            try {
                return t.getOrder();
            } catch (Throwable ex) {
                return 0L;
            }
        }

        void enqueue(TaskItem item) {
            if (!running.get()) return;
            queue.put(item);
        }

        void start() {
            worker.execute(() -> {
                while (running.get() || !queue.isEmpty()) {
                    try {
                        TaskItem item = queue.take();
                        try {
                            // 在工作线程调用 executor.execute 保持同步执行，并复用 executor 的锁/重试等逻辑
                            item.executor.execute(item.task);
                        } catch (Throwable ex) {
                            // 防止单个任务异常导致线程退出
                            ex.printStackTrace();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        void shutdown() {
            running.set(false);
            worker.shutdownNow();
        }
    }
}
