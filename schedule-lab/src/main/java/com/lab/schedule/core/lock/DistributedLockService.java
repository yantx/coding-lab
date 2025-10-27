package com.lab.schedule.core.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson的分布式锁服务
 */
@Component
public class DistributedLockService {
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);
    // 默认获取锁的等待时间（秒）
    private static final long DEFAULT_WAIT_TIME = 10;
    // 默认锁的持有时间（秒）
    private static final long DEFAULT_LEASE_TIME = 60;

    private final RedissonClient redissonClient;

    @Autowired
    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 使用默认等待时间和租约时间尝试获取锁
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
    }

    /**
     * 使用自定义等待时间和租约时间尝试获取锁
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("获取分布式锁被中断", e);
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            logger.error("释放分布式锁失败", e);
        }
    }

    /**
     * 检查锁是否被当前线程持有
     */
    public boolean isHeldByCurrentThread(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock != null && lock.isHeldByCurrentThread();
        } catch (Exception e) {
            logger.error("检查锁状态失败", e);
            return false;
        }
    }
}