package com.lab.chche.controller;

import com.lab.chche.service.CacheStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheStatsController {

    private final CacheStatsService cacheStatsService;

    @Autowired
    public CacheStatsController(CacheStatsService cacheStatsService) {
        this.cacheStatsService = cacheStatsService;
    }

    /**
     * 获取所有缓存的统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Map<String, Object>>> getAllCacheStats() {
        return ResponseEntity.ok(cacheStatsService.getCacheStats());
    }

    /**
     * 获取指定缓存的统计信息
     */
    @GetMapping("/stats/{cacheName}")
    public ResponseEntity<?> getCacheStats(@PathVariable String cacheName) {
        Map<String, Object> stats = cacheStatsService.getCacheStats(cacheName);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取指定缓存的命中率
     */
    @GetMapping("/hit-rate/{cacheName}")
    public ResponseEntity<Map<String, Object>> getCacheHitRate(@PathVariable String cacheName) {
        Map<String, Object> stats = cacheStatsService.getCacheStats(cacheName);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("cacheName", cacheName);
        result.put("hitRate", stats.get("hitRate"));
        result.put("missRate", stats.get("missRate"));
        
        return ResponseEntity.ok(result);
    }
}
