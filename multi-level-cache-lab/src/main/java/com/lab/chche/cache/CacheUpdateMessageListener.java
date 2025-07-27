package com.lab.chche.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class CacheUpdateMessageListener implements MessageListener {

    private final CacheManager caffeineCacheManager;
    private final ObjectMapper objectMapper;

    public CacheUpdateMessageListener(CacheManager caffeineCacheManager, ObjectMapper objectMapper) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if (!"cache:update".equals(channel)) {
                return;
            }

            String json = new String(message.getBody());
            MultiLevelCacheManager.CacheUpdateEvent event = objectMapper.readValue(
                json, MultiLevelCacheManager.CacheUpdateEvent.class);

            // Update local L1 cache
            Cache cache = caffeineCacheManager.getCache(event.getCacheName());
            if (cache != null) {
                cache.put(event.getKey(), event.getValue());
            }
        } catch (Exception e) {
            // Log error but don't propagate to prevent message listener from dying
            e.printStackTrace();
        }
    }
}
