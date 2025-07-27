package com.lab.chche.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class CacheEvictMessageListener implements MessageListener {

    private final CacheManager caffeineCacheManager;
    private final ObjectMapper objectMapper;

    public CacheEvictMessageListener(CacheManager caffeineCacheManager, ObjectMapper objectMapper) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if (!"cache:evict".equals(channel)) {
                return;
            }

            String json = new String(message.getBody());
            MultiLevelCacheManager.CacheEvictionEvent event = objectMapper.readValue(
                json, MultiLevelCacheManager.CacheEvictionEvent.class);

            // Evict from local L1 cache
            Cache cache = caffeineCacheManager.getCache(event.getCacheName());
            if (cache != null) {
                cache.evict(event.getKey());
            }
        } catch (Exception e) {
            // Log error but don't propagate to prevent message listener from dying
            e.printStackTrace();
        }
    }
}
