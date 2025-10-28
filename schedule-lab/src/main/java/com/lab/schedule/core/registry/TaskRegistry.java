package com.lab.schedule.core.registry;

import com.lab.schedule.annotation.ScheduledTask;
import com.lab.schedule.core.manager.DistributedTaskManager;
import com.lab.schedule.core.model.TaskDomain;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

/**
 * 扫描带 {@code @ScheduledTask} 的 bean 方法并注册到 DistributedTaskManager。
 * 在 ContextRefreshedEvent 时执行一次扫描并注册。
 */
public class TaskRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext context;
    private final DistributedTaskManager manager;

    public TaskRegistry(ApplicationContext context, DistributedTaskManager manager) {
        this.context = context;
        this.manager = manager;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        scanAndRegister();
    }

    private void scanAndRegister() {
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Method[] methods = bean.getClass().getMethods();
            for (Method method : methods) {
                ScheduledTask ann = AnnotatedElementUtils.findMergedAnnotation(method, ScheduledTask.class);
                if (ann != null) {
                    TaskDomain task = buildTaskDomain(bean, method, ann);
                    // 如果任务被标记为未启用，则跳过注册与调度
                    if (!task.isEnabled()) {
                        continue;
                    }
                    manager.registerTask(task);
                }
            }
        }
    }

    private TaskDomain buildTaskDomain(Object bean, Method method, ScheduledTask ann) {
        String name = safeString(tryInvokeAnnotation(ann, "name"), bean.getClass().getSimpleName() + "#" + method.getName());
        String group = safeString(tryInvokeAnnotation(ann, "group"), "default");

        TaskDomain task = new TaskDomain(name, group, ann);
        task.setTargetBean(bean);
        task.setTargetMethod(method);

        // 尝试设置常见字段（注解需要对应的属性名）
        setIfPresent(task, "setCron", tryInvokeAnnotation(ann, "cron"));
        setIfPresentLong(task, "setFixedRate", tryInvokeAnnotation(ann, "fixedRate"));
        setIfPresentLong(task, "setFixedDelay", tryInvokeAnnotation(ann, "fixedDelay"));
        setIfPresentLong(task, "setInitialDelay", tryInvokeAnnotation(ann, "initialDelay"));
        setIfPresentBool(task, "setAsync", tryInvokeAnnotation(ann, "async"));
        setIfPresentBool(task, "setEnabled", tryInvokeAnnotation(ann, "enabled"));
        setIfPresent(task, "setDescription", tryInvokeAnnotation(ann, "description"));
        setIfPresent(task, "setOrder", tryInvokeAnnotation(ann, "order"));
        setIfPresent(task, "setNodeId", tryInvokeAnnotation(ann, "nodeId"));
        // 其它字段可按需扩展

        return task;
    }

    // 反射安全读取注解属性（若不存在或调用失败返回 null）
    private Object tryInvokeAnnotation(ScheduledTask ann, String methodName) {
        try {
            Method m = ann.getClass().getMethod(methodName);
            return m.invoke(ann);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeString(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o);
        return s.isEmpty() ? def : s;
    }

    private void setIfPresent(TaskDomain task, String setterName, Object value) {
        if (value == null) return;
        try {
            Method setter = TaskDomain.class.getMethod(setterName, String.class);
            setter.invoke(task, String.valueOf(value));
        } catch (NoSuchMethodException ignored) {
            // 忽略类型不匹配，尝试其他 setter 形式在下面处理
        } catch (Exception ignored) {}
    }

    private void setIfPresentLong(TaskDomain task, String setterName, Object value) {
        if (value == null) return;
        try {
            Method setter = TaskDomain.class.getMethod(setterName, long.class);
            long v = (value instanceof Number) ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
            setter.invoke(task, v);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {}
    }

    private void setIfPresentBool(TaskDomain task, String setterName, Object value) {
        if (value == null) return;
        try {
            Method setter = TaskDomain.class.getMethod(setterName, boolean.class);
            boolean v = (value instanceof Boolean) ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
            setter.invoke(task, v);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {}
    }

    // 对外简单 API
    public void register(TaskDomain task) {
        if (task == null || !task.isEnabled()) return;
        manager.registerTask(task);
    }

    public void unregister(String name) {
        manager.unregisterTask(name);
    }

    public boolean trigger(String name) {
        return manager.triggerTask(name);
    }

    public Map<String, Object> getTaskInfoSnapshot() {
        // Java8 兼容实现，后续可以扩展为返回 manager 中的详细任务状态
        Map<String, Object> info = new HashMap<>();
        info.put("status", "ok");
        return info;
    }
}
