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
            String body = new String(message.getBody());

            // 处理缓存清空事件
            if ("cache:clear".equals(channel)) {
                String cacheName = body;
                Cache cache = caffeineCacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
                return;
            }

            // 原有的缓存失效事件处理
            if ("cache:evict".equals(channel)) {
                MultiLevelCacheManager.CacheEvictionEvent event = objectMapper.readValue(
                        body, MultiLevelCacheManager.CacheEvictionEvent.class);

                // 从本地 L1 缓存中移除
                Cache cache = caffeineCacheManager.getCache(event.getCacheName());
                if (cache != null) {
                    cache.evict(event.getKey());
                }
            }
        } catch (Exception e) {
            // 记录错误但不要传播，防止消息监听器挂掉
            e.printStackTrace();
        }
    }
}