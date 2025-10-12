package com.lab.chche.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class MultiLevelCacheManager implements CacheManager {

    private final CacheManager l1CacheManager; // Caffeine缓存管理器
    private final CacheManager l2CacheManager; // Redis缓存管理器
    private final RedisTemplate<String, Object> redisTemplate;

    public MultiLevelCacheManager(CacheManager l1CacheManager,
                                  CacheManager l2CacheManager,
                                  RedisTemplate<String, Object> redisTemplate) {
        this.l1CacheManager = l1CacheManager;
        this.l2CacheManager = l2CacheManager;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Cache getCache(String name) {
        Cache l1Cache = l1CacheManager.getCache(name);
        Cache l2Cache = l2CacheManager.getCache(name);
        return new MultiLevelCache(name, l1Cache, l2Cache, redisTemplate);
    }

    /**
     * 获取底层Caffeine缓存管理器，用于统计信息
     */
    public com.github.benmanes.caffeine.cache.Cache<Object, Object> getNativeCache(String name) {
        Cache cache = l1CacheManager.getCache(name);
        if (cache instanceof org.springframework.cache.caffeine.CaffeineCache) {
            return ((org.springframework.cache.caffeine.CaffeineCache) cache).getNativeCache();
        }
        return null;
    }
    
//    /**
//     * 获取缓存统计信息
//     */
//    public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats(String name) {
//        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = getNativeCache(name);
//        return nativeCache != null ? nativeCache.stats() : null;
//    }

    @Override
    public Collection<String> getCacheNames() {
        return l2CacheManager.getCacheNames(); // 使用L2缓存名称作为数据源
    }

    public Map<String, Object> getCacheStats(String cacheName) {
        Cache cache = getCache(cacheName);
        if (cache instanceof MultiLevelCache) {
            return ((MultiLevelCache) cache).getStats();
        }
        return null;
    }

    public static class MultiLevelCache implements Cache {
        private final String name;
        private final Cache l1Cache;
        private final Cache l2Cache;
        private final RedisTemplate<String, Object> redisTemplate;
        // 在 MultiLevelCache 类中添加这些字段
        private final CacheStats l1Stats = new CacheStats();
        private final CacheStats l2Stats = new CacheStats();

        public MultiLevelCache(String name, Cache l1Cache, Cache l2Cache, 
                             RedisTemplate<String, Object> redisTemplate) {
            this.name = name;
            this.l1Cache = l1Cache;
            this.l2Cache = l2Cache;
            this.redisTemplate = redisTemplate;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return l2Cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            // Try L1 cache first
            ValueWrapper value = l1Cache.get(key);
            if (value != null) {
                l1Stats.recordHit();
                return value;
            }
            l1Stats.recordMiss();


            // If not in L1, try L2 cache
            value = l2Cache.get(key);
            if (value != null) {
                l2Stats.recordHit();
                // Populate L1 cache from L2
                l1Cache.put(key, value.get());
            } else {
                l2Stats.recordMiss();
            }
            
            return value;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            // Try L1 cache first
            T value = l1Cache.get(key, type);
            if (value != null) {
                l1Stats.recordHit();
                return value;
            }
            l1Stats.recordMiss();
            // If not in L1, try L2 cache
            value = l2Cache.get(key, type);
            if (value != null) {
                l2Stats.recordMiss();
                // Populate L1 cache from L2
                l1Cache.put(key, value);
            } else {
                l2Stats.recordMiss();
            }
            
            return value;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            try {
                // Try L1 cache first
                T value = l1Cache.get(key, valueLoader);
                if (value != null) {
                    l1Stats.recordHit();
                    return value;
                }
                l1Stats.recordMiss();
                
                // If not in L1, try L2 cache
                value = l2Cache.get(key, valueLoader);
                if (value != null) {
                    l2Stats.recordHit();
                    // Populate L1 cache from L2
                    l1Cache.put(key, value);
                }else {
                    l2Stats.recordMiss();
                }
                
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            // Write to both caches
            l2Cache.put(key, value);
            l1Cache.put(key, value);
            
            // Publish cache update event for other instances
            publishCacheUpdate(key, value);
        }

        @Override
        public void evict(Object key) {
            // Remove from both caches
            l2Cache.evict(key);
            l1Cache.evict(key);
            
            // Publish cache eviction event for other instances
            publishCacheEviction(key);
        }

        @Override
        public void clear() {
            l2Cache.clear();
            l1Cache.clear();

            // 发布缓存清空事件到其他实例
            redisTemplate.convertAndSend("cache:clear", name);

        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            // 这是一个简化实现
            ValueWrapper existingValue = get(key);
            if (existingValue == null) {
                put(key, value);
                return null;
            }
            return existingValue;
        }

        private void publishCacheUpdate(Object key, Object value) {
            // 发布到Redis pub/sub，通知其他实例更新它们的L1缓存
            redisTemplate.convertAndSend("cache:update", new CacheUpdateEvent(name, key, value));
        }

        private void publishCacheEviction(Object key) {
            // 发布到Redis pub/sub，通知其他实例使它们的L1缓存失效
            redisTemplate.convertAndSend("cache:evict", new CacheEvictionEvent(name, key));
        }
        // 添加获取统计信息的方法
        public Map<String, Object> getStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("l1", l1Stats.snapshot());
            stats.put("l2", l2Stats.snapshot());
            return stats;
        }
    }

    // 用于缓存同步的事件类
    public static class CacheUpdateEvent {
        private final String cacheName;
        private final Object key;
        private final Object value;

        public CacheUpdateEvent(String cacheName, Object key, Object value) {
            this.cacheName = cacheName;
            this.key = key;
            this.value = value;
        }

        // Getters
        public String getCacheName() { return cacheName; }
        public Object getKey() { return key; }
        public Object getValue() { return value; }
    }

    public static class CacheEvictionEvent {
        private final String cacheName;
        private final Object key;

        public CacheEvictionEvent(String cacheName, Object key) {
            this.cacheName = cacheName;
            this.key = key;
        }

        // Getters
        public String getCacheName() { return cacheName; }
        public Object getKey() { return key; }
    }

    // 在 MultiLevelCache 类中添加这个内部类
    private static class CacheStats {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();

        public void recordHit() {
            hits.incrementAndGet();
        }

        public void recordMiss() {
            misses.incrementAndGet();
        }

        public Map<String, Object> snapshot() {
            long hitCount = hits.get();
            long missCount = misses.get();
            long total = hitCount + missCount;
            double hitRate = total > 0 ? (double) hitCount / total : 0;
            double missRate = total > 0 ? (double) missCount / total : 0;

            Map<String, Object> stats = new HashMap<>();
            stats.put("hits", hitCount);
            stats.put("misses", missCount);
            stats.put("total", total);
            stats.put("hitRate", String.format("%.2f%%", hitRate * 100));
            stats.put("missRate", String.format("%.2f%%", missRate * 100));
            return stats;
        }
    }

}
