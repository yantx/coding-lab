package com.lab.schedule.web;

import com.lab.schedule.core.DistributedTaskScheduler;
import com.lab.schedule.core.executor.DistributedTaskExecutor;
import com.lab.schedule.core.model.TaskDomain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final DistributedTaskExecutor taskExecutor;
    private final DistributedTaskScheduler taskScheduler;

    @Autowired
    public TaskController(DistributedTaskExecutor taskExecutor,
                          DistributedTaskScheduler taskScheduler) {
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    @GetMapping
    public Map<String, TaskDomain> getAllTasks() {
        return taskScheduler.getScheduledTasksInfo();
    }

    @PostMapping("/{taskName}/trigger")
    public TaskDomain triggerTask(@PathVariable String taskName) {
        boolean triggered = taskExecutor.triggerTask(taskName);
        return triggered ? taskScheduler.getScheduledTasksInfo().get(taskName) : null;
    }

    @PostMapping("/{taskName}/pause")
    public String pauseTask(@PathVariable String taskName) {
        boolean paused = taskExecutor.pauseTask(taskName);
        return paused ? "任务已暂停" : "任务暂停失败或任务不存在";
    }

    @PostMapping("/{taskName}/resume")
    public String resumeTask(@PathVariable String taskName) {
        boolean resumed = taskExecutor.resumeTask(taskName);
        return resumed ? "任务已恢复" : "任务恢复失败或任务不存在";
    }
}
