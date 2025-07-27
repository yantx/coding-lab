package com.lab.chche.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.lab.chche.cache.MultiLevelCacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheStatsService {
    
    private final MultiLevelCacheManager cacheManager;
    private final Map<String, CacheStats> cacheStatsMap = new ConcurrentHashMap<>();
    
    public CacheStatsService(MultiLevelCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * 获取所有缓存的统计信息
     */
    public Map<String, Map<String, Object>> getCacheStats() {
        Map<String, Map<String, Object>> stats = new HashMap<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) caffeineCache.getNativeCache();
                
                // 获取Caffeine的统计信息
                CacheStats statsCounter = nativeCache.stats();
                
                Map<String, Object> statMap = new HashMap<>();
                statMap.put("hitCount", statsCounter.hitCount());
                statMap.put("missCount", statsCounter.missCount());
                statMap.put("loadSuccessCount", statsCounter.loadSuccessCount());
                statMap.put("loadFailureCount", statsCounter.loadFailureCount());
                statMap.put("totalLoadTime", statsCounter.totalLoadTime());
                statMap.put("evictionCount", statsCounter.evictionCount());
                statMap.put("evictionWeight", statsCounter.evictionWeight());
                
                // 计算命中率
                long requestCount = statsCounter.requestCount();
                double hitRate = requestCount == 0 ? 1.0 : statsCounter.hitRate();
                double missRate = requestCount == 0 ? 0.0 : statsCounter.missRate();
                
                statMap.put("requestCount", requestCount);
                statMap.put("hitRate", String.format("%.2f%%", hitRate * 100));
                statMap.put("missRate", String.format("%.2f%%", missRate * 100));
                
                stats.put(cacheName, statMap);
                
                // 更新缓存统计信息
                cacheStatsMap.put(cacheName, statsCounter);
            }
        }
        
        return stats;
    }
    
    /**
     * 获取指定缓存的统计信息
     */
    public Map<String, Object> getCacheStats(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        
        if (cache instanceof CaffeineCache) {
            CaffeineCache caffeineCache = (CaffeineCache) cache;
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) caffeineCache.getNativeCache();
            
            CacheStats statsCounter = nativeCache.stats();
            
            Map<String, Object> statMap = new HashMap<>();
            statMap.put("hitCount", statsCounter.hitCount());
            statMap.put("missCount", statsCounter.missCount());
            statMap.put("loadSuccessCount", statsCounter.loadSuccessCount());
            statMap.put("loadFailureCount", statsCounter.loadFailureCount());
            statMap.put("totalLoadTime", statsCounter.totalLoadTime());
            statMap.put("evictionCount", statsCounter.evictionCount());
            statMap.put("evictionWeight", statsCounter.evictionWeight());
            
            // 计算命中率
            long requestCount = statsCounter.requestCount();
            double hitRate = requestCount == 0 ? 1.0 : statsCounter.hitRate();
            double missRate = requestCount == 0 ? 0.0 : statsCounter.missRate();
            
            statMap.put("requestCount", requestCount);
            statMap.put("hitRate", String.format("%.2f%%", hitRate * 100));
            statMap.put("missRate", String.format("%.2f%%", missRate * 100));
            
            // 更新缓存统计信息
            cacheStatsMap.put(cacheName, statsCounter);
            
            return statMap;
        }
        
        return null;
    }
    
    /**
     * 获取缓存的命中率
     */
    public double getCacheHitRate(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats == null) {
            return 0.0;
        }
        long requestCount = stats.requestCount();
        return requestCount == 0 ? 0.0 : stats.hitRate();
    }
    
    /**
     * 获取缓存的未命中率
     */
    public double getCacheMissRate(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats == null) {
            return 0.0;
        }
        long requestCount = stats.requestCount();
        return requestCount == 0 ? 0.0 : stats.missRate();
    }
}
