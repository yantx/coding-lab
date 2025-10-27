package com.lab.schedule.task;

import com.lab.schedule.annotation.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例定时任务类
 */
@Component
public class ExampleTasks {
    
    private static final Logger logger = LoggerFactory.getLogger(ExampleTasks.class);
    // 分布式任务，不保证顺序
    @ScheduledTask(
            name = "distributedTask",
            group = "default",
            cron = "0/5 * * * * ?",
            async = true  // 使用异步执行
    )
    public void distributedTask() {
        // 这个任务会在分布式环境下只执行一次
        logger.info("这个任务会在分布式环境下只执行一次 - 当前时间: {}", System.currentTimeMillis());

    }

    // 需要顺序执行的任务组
    @ScheduledTask(
            name = "orderTask1",
            group = "orderGroup1",  // 非default分组，会按顺序执行
            order = 1,              // 执行顺序
            cron = "0/5 * * * * ?"
    )
    public void orderTask1() {
        // 这个任务会在 orderTask2 之前执行
        // 在分布式环境下，同一个分组的任务会按顺序执行
        logger.info("这个任务会在 orderTask2 之前执行 - 当前时间: {}", System.currentTimeMillis());

    }

    @ScheduledTask(
            name = "orderTask2",
            group = "orderGroup1",  // 与 orderTask1 同组
            order = 2,              // 在 orderTask1 之后执行
            cron = "0/5 * * * * ?"
    )
    public void orderTask2() {
        // 这个任务会在 orderTask1 执行完成后执行
        logger.info("这个任务会在 orderTask1 执行完成后执行 - 当前时间: {}", System.currentTimeMillis());

    }
    /**
     * 每10秒执行一次的任务
     */
    /**
     * 顺序组中的第一个任务，order=1，会第二个执行
     */
    @ScheduledTask(name = "exampleTask1", cron = "0/5 * * * * ?",
                  group = "sequentialGroup",
                  order = 1,
                  description = "示例任务1，order=1，在顺序组中第二个执行")
    public void task1() {
        logger.info("开始执行示例任务1 (order=1) - 当前时间: {}", System.currentTimeMillis());
        try {
            // 模拟任务执行耗时
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("完成执行示例任务1 (order=1) - 当前时间: {}", System.currentTimeMillis());
    }
    
    /**
     * 顺序组中的第二个任务，order=0，会第一个执行（order值越小优先级越高）
     */
    @ScheduledTask(name = "exampleTask1a", cron = "0/10 * * * * ?",
                  group = "sequentialGroup",
                  order = 0,
                  description = "示例任务1a，order=0，在顺序组中第一个执行")
    public void task1a() {
        logger.info("开始执行示例任务1a (order=0) - 当前时间: {}", System.currentTimeMillis());
        try {
            // 模拟任务执行耗时
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("完成执行示例任务1a (order=0) - 当前时间: {}", System.currentTimeMillis());
    }
    
    /**
     * 顺序组中的第三个任务，order=2，会第三个执行
     */
    @ScheduledTask(name = "exampleTask1b", cron = "0/10 * * * * ?",
                  group = "sequentialGroup",
                  order = 2,
                  description = "示例任务1b，order=2，在顺序组中第三个执行")
    public void task1b() {
        logger.info("开始执行示例任务1b (order=2) - 当前时间: {}", System.currentTimeMillis());
        try {
            // 模拟任务执行耗时
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("完成执行示例任务1b (order=2) - 当前时间: {}", System.currentTimeMillis());
    }
    
    /**
     * 固定间隔执行的任务，每隔30秒执行一次
     */
    @ScheduledTask(name = "exampleTask2", fixedRate = 30000,
                  description = "示例任务2，固定间隔30秒执行一次")
    public void task2() {
        logger.info("执行示例任务2 - 当前时间: {}", System.currentTimeMillis());
    }
    
    /**
     * 固定延迟执行的任务，每次执行完成后延迟45秒再次执行
     */
    @ScheduledTask(name = "exampleTask3", fixedDelay = 45000,
                  description = "示例任务3，固定延迟45秒执行一次")
    public void task3() {
        try {
            logger.info("开始执行示例任务3 - 当前时间: {}", System.currentTimeMillis());
            // 模拟任务执行耗时
            Thread.sleep(5000);
            logger.info("完成执行示例任务3 - 当前时间: {}", System.currentTimeMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("任务执行被中断", e);
        }
    }
}
