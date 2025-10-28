package com.lab.schedule.web;

import com.lab.schedule.core.manager.DistributedTaskManager;
import com.lab.schedule.core.registry.TaskRegistry;
import com.lab.schedule.core.model.TaskDomain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

/**
 * 合并后的任务管理控制器：提供注册/注销/触发/查询接口
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final DistributedTaskManager manager;
    private final TaskRegistry registry;

    @Autowired
    public TaskController(DistributedTaskManager manager, TaskRegistry registry) {
        this.manager = manager;
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> list() {
        return registry.getTaskInfoSnapshot();
    }

    @PostMapping
    public Map<String, Object> register(@RequestBody TaskDomain task) {
        manager.registerTask(task);
        Map<String, Object> resp = new HashMap<>();
        resp.put("result", "registered");
        resp.put("name", task != null ? task.getName() : null);
        return resp;
    }

    @DeleteMapping("/{name}")
    public Map<String, Object> unregister(@PathVariable String name) {
        manager.unregisterTask(name);
        Map<String, Object> resp = new HashMap<>();
        resp.put("result", "unregistered");
        resp.put("name", name);
        return resp;
    }

    @PostMapping("/{name}/trigger")
    public Map<String, Object> trigger(@PathVariable String name) {
        boolean ok = manager.triggerTask(name);
        Map<String, Object> resp = new HashMap<>();
        resp.put("name", name);
        resp.put("triggered", ok);
        return resp;
    }
}
