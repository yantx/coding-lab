package com.lab.schedule.core.registry;

import com.lab.schedule.core.lock.DistributedLockService;
import com.lab.schedule.core.model.TaskDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分布式任务注册服务
 * 负责处理任务的分布式注册、发现和状态同步
 */
public class TaskRegistryService {
    private static final Logger logger = LoggerFactory.getLogger(TaskRegistryService.class);
    
    // Redis key 前缀
    private static final String REGISTRY_LOCK_KEY = "schedule:registry:lock";
    private static final String TASK_KEY_PREFIX = "schedule:task:";
    private static final String TASK_INSTANCE_MAP_KEY = "schedule:task:instance:map";
    private static final String TASK_HEARTBEAT_PREFIX = "schedule:task:heartbeat:";
    
    // 默认配置
    private static final long DEFAULT_HEARTBEAT_INTERVAL = 30; // 默认心跳间隔30秒
    private static final long LOCK_TIMEOUT = 30; // 分布式锁超时时间30秒
    private static final long LOCK_WAIT_TIME = 5; // 获取锁等待时间5秒

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${schedule.registry.heartbeat-interval:30}")
    private long heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    
    private final DistributedLockService lockService;
    private final RedisTemplate<String, Object> redisTemplate;
    private String instanceId; // 格式为 ip:port

    @Autowired
    public TaskRegistryService(
            DistributedLockService lockService,
            RedisTemplate<String, Object> redisTemplate) {
        this.lockService = lockService;
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void init() {
        this.instanceId = getInstanceId();
        logger.info("分布式任务注册服务已初始化，实例ID: {}", instanceId);
    }

    /**
     * 取消注册任务
     * @param taskName 要取消注册的任务名称
     * @return 如果取消注册成功返回true，否则返回false
     */
    public boolean unregisterTask(String taskName) {
        String lockKey = getTaskLockKey(taskName);
        try {
            if (lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                try {
                    String registeredInstance = getRegisteredInstance(taskName);
                    
                    if (instanceId.equals(registeredInstance)) {
                        // 清理任务相关数据
                        String taskKey = getTaskKey(taskName);
                        redisTemplate.delete(taskKey);
                        redisTemplate.opsForHash().delete(TASK_INSTANCE_MAP_KEY, taskName);
                        redisTemplate.delete(getHeartbeatKey(taskName));
                        
                        logger.info("任务取消注册成功: {}", taskName);
                        return true;
                    } else {
                        logger.warn("取消注册任务失败: {} 不属于当前实例", taskName);
                        return false;
                    }
                } finally {
                    lockService.unlock(lockKey);
                }
            }
            logger.warn("获取任务锁失败: {}", taskName);
            return false;
        } catch (Exception e) {
            logger.error("取消注册任务异常: " + taskName, e);
            return false;
        }
    }

    /**
     * 注册任务到分布式注册中心
     * @param task 要注册的任务
     * @return 如果注册成功返回true，如果任务已存在或注册失败返回false
     */
    public boolean registerTask(TaskDomain task) {
        String taskName = task.getName();
        String lockKey = getTaskLockKey(taskName);
        
        try {
            if (lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                try {
                    // 检查任务是否已被其他实例注册
                    String registeredInstance = getRegisteredInstance(taskName);
                    if (registeredInstance != null && !instanceId.equals(registeredInstance)) {
                        logger.warn("任务 {} 已被实例 {} 注册", taskName, registeredInstance);
                        return false;
                    }
                    
                    // 更新任务信息
                    task.setLastHeartbeat(System.currentTimeMillis());
                    String taskKey = getTaskKey(taskName);
                    
                    // 保存任务信息
                    redisTemplate.opsForValue().set(
                        taskKey, 
                        task, 
                        heartbeatInterval * 3, 
                        TimeUnit.SECONDS
                    );
                    
                    // 更新实例映射
                    redisTemplate.opsForHash().put(
                        TASK_INSTANCE_MAP_KEY, 
                        taskName, 
                        instanceId
                    );
                    
                    // 更新心跳
                    updateHeartbeat(taskName);
                    
                    logger.info("任务注册成功: {}, 分组: {}", taskName, task.getGroup());
                    return true;
                } finally {
                    lockService.unlock(lockKey);
                }
            }
            logger.warn("获取任务锁失败: {}", taskName);
            return false;
        } catch (Exception e) {
            logger.error("注册任务异常: " + taskName, e);
            return false;
        }
    }

    /**
     * 获取当前实例注册的所有任务
     * @return 任务集合
     */
    public List<TaskDomain> getLocalTasks() {
        try {
            Map<Object, Object> taskInstanceMap = redisTemplate.opsForHash().entries(TASK_INSTANCE_MAP_KEY);
            return taskInstanceMap.entrySet().stream()
                    .filter(entry -> instanceId.equals(entry.getValue()))
                    .map(entry -> getTask(entry.getKey().toString()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取本地任务列表异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取所有已注册的任务（包括其他实例注册的任务）
     * @return 所有任务信息列表
     */
    public List<TaskDomain> getAllTasks() {
        try {
            Set<Object> taskNames = redisTemplate.opsForHash().keys(TASK_INSTANCE_MAP_KEY);
            if (CollectionUtils.isEmpty(taskNames)) {
                return Collections.emptyList();
            }
            
            List<TaskDomain> tasks = new ArrayList<>(taskNames.size());
            for (Object name : taskNames) {
                TaskDomain task = getTask(name.toString());
                if (task != null) {
                    tasks.add(task);
                }
            }
            return tasks;
        } catch (Exception e) {
            logger.error("获取所有任务异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取任务信息
     * @param taskName 任务名称
     * @return 任务信息，如果不存在返回null
     */
    public TaskDomain getTask(String taskName) {
        try {
            String taskKey = getTaskKey(taskName);
            return (TaskDomain) redisTemplate.opsForValue().get(taskKey);
        } catch (Exception e) {
            logger.error("获取任务信息异常: " + taskName, e);
            return null;
        }
    }

    /**
     * 检查任务是否已注册
     * @param taskName 要检查的任务名称
     * @return 如果任务已注册返回true，否则返回false
     */
    public boolean isTaskRegistered(String taskName) {
        try {
            return redisTemplate.opsForHash().hasKey(TASK_INSTANCE_MAP_KEY, taskName);
        } catch (Exception e) {
            logger.error("检查任务注册状态异常: " + taskName, e);
            return false;
        }
    }
    
    /**
     * 获取任务注册的实例ID
     * @param taskName 任务名称
     * @return 注册该任务的实例ID，如果未注册返回null
     */
    public String getRegisteredInstance(String taskName) {
        try {
            Object instance = redisTemplate.opsForHash().get(TASK_INSTANCE_MAP_KEY, taskName);
            return instance != null ? instance.toString() : null;
        } catch (Exception e) {
            logger.error("获取任务注册实例异常: " + taskName, e);
            return null;
        }
    }

    /**
     * 更新任务心跳
     * @param taskName 任务名称
     */
    private void updateHeartbeat(String taskName) {
        try {
            String heartbeatKey = getHeartbeatKey(taskName);
            long now = System.currentTimeMillis();
            redisTemplate.opsForValue().set(
                heartbeatKey, 
                now, 
                heartbeatInterval * 2, // 心跳超时时间为2个心跳周期
                TimeUnit.SECONDS
            );
            logger.trace("更新任务心跳: {}", taskName);
        } catch (Exception e) {
            logger.error("更新任务心跳异常: " + taskName, e);
        }
    }
    
    /**
     * 发送心跳以保持任务注册活跃
     * 定期更新任务的心跳时间，并清理过期的任务
     */
    @Scheduled(fixedRate = 10000) // 每10秒执行一次
    public void heartbeat() {
        try {
            List<TaskDomain> localTasks = getLocalTasks();
            if (localTasks.isEmpty()) {
                return;
            }

            String lockKey = REGISTRY_LOCK_KEY + ":heartbeat:" + instanceId;
            if (lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                try {
                    long now = System.currentTimeMillis();
                    
                    // 更新当前实例所有任务的心跳
                    for (TaskDomain task : localTasks) {
                        String taskName = task.getName();
                        
                        // 检查任务是否仍然属于当前实例
                        String registeredInstance = getRegisteredInstance(taskName);
                        if (instanceId.equals(registeredInstance)) {
                            // 更新任务最后活跃时间
                            task.setLastHeartbeat(now);
                            String taskKey = getTaskKey(taskName);
                            redisTemplate.opsForValue().set(
                                taskKey, 
                                task,
                                heartbeatInterval * 3, 
                                TimeUnit.SECONDS
                            );
                            
                            // 更新心跳
                            updateHeartbeat(taskName);
                        }
                    }
                } finally {
                    lockService.unlock(lockKey);
                }
            }
        } catch (Exception e) {
            logger.error("任务心跳处理异常", e);
        }
    }
    
    /**
     * 清理过期的任务注册信息
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    protected void cleanupExpiredTasks() {
        try {
            // 获取所有任务实例映射
            Map<Object, Object> taskInstanceMap = redisTemplate.opsForHash().entries(TASK_INSTANCE_MAP_KEY);
            if (CollectionUtils.isEmpty(taskInstanceMap)) {
                return;
            }
            for (Map.Entry<Object, Object> entry : taskInstanceMap.entrySet()) {
                String taskName = entry.getKey().toString();
                String lockKey = getTaskLockKey(taskName);
                try {
                    if (lockService.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                        try {
                            // 检查心跳是否超时
                            String heartbeatKey = getHeartbeatKey(taskName);
                            Long lastHeartbeat = (Long) redisTemplate.opsForValue().get(heartbeatKey);
                            
                            if (lastHeartbeat == null || 
                                (System.currentTimeMillis() - lastHeartbeat) > (heartbeatInterval * 2 * 1000)) {
                                // 心跳超时，清理任务
                                String taskKey = getTaskKey(taskName);
                                redisTemplate.delete(taskKey);
                                redisTemplate.opsForHash().delete(TASK_INSTANCE_MAP_KEY, taskName);
                                redisTemplate.delete(heartbeatKey);
                                
                                logger.info("已清理过期任务: {}", taskName);
                            }
                        } finally {
                            lockService.unlock(lockKey);
                        }
                    }
                } catch (Exception e) {
                    logger.error("清理任务异常: {}", taskName, e);
                }
            }
        } catch (Exception e) {
            logger.error("清理过期任务异常", e);
        }
    }

    /**
     * 获取实例ID（主机名:端口）
     */
    private String getInstanceId() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName() + ":" + serverPort;
        } catch (Exception e) {
            return "unknown:" + System.currentTimeMillis();
        }
    }
    
    /**
     * 获取任务的分布式锁键
     */
    private String getTaskLockKey(String taskName) {
        return REGISTRY_LOCK_KEY + ":task:" + taskName;
    }

    /**
     * 获取任务在分布式注册中心中的键
     */
    private String getTaskKey(String taskName) {
        return TASK_KEY_PREFIX + taskName;
    }
    
    /**
     * 获取任务心跳键
     */
    private String getHeartbeatKey(String taskName) {
        return TASK_HEARTBEAT_PREFIX + taskName;
    }
}
