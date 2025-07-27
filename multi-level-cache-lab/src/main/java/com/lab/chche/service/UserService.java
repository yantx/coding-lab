package com.lab.chche.service;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@CacheConfig(cacheManager = "multiLevelCacheManager")
public class UserService {
    
    // Simulating a database
    private final Map<Long, User> userDatabase = new HashMap<>();

    
    // Initialize with some sample data
    public UserService() {
        userDatabase.put(1L, new User(1L, "John Doe", "john@example.com"));
        userDatabase.put(2L, new User(2L, "Jane Smith", "jane@example.com"));
    }
    
    @Cacheable(value = "userCache", key = "#id")
    public User getUserById(Long id) {
        // Simulate database delay
        simulateSlowService();
        return userDatabase.get(id);
    }

    // This method would be called to refresh the cache
    @CachePut(value = "userCache", key = "#user.id")
    public User refreshUser(User user) {
        // Just return the user to update the cache
        return user;
    }
    
    private void simulateSlowService() {
        try {
            long time = 3000L;
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
    
    /**
     * 获取所有用户列表（带缓存）
     * 使用固定key 'allUsers' 来缓存整个列表
     */
    @Cacheable(value = "userListCache", key = "'allUsers'")
    public List<User> getAllUsers() {
        simulateSlowService();
        return new ArrayList<>(userDatabase.values());
    }
    
    /**
     * 更新用户信息时，同时更新用户缓存和用户列表缓存
     */
    @CachePut(value = "userCache", key = "#user.id")
    @CacheEvict(value = "userListCache", key = "'allUsers'")
    public User updateUser(User user) {
        // 更新数据库
        userDatabase.put(user.getId(), user);
        return user;
    }
    
    /**
     * 删除用户时，同时从用户缓存和用户列表缓存中移除
     */
    @CacheEvict(value = {"userCache", "userListCache"}, allEntries = true)
    public void deleteUser(Long id) {
        userDatabase.remove(id);
    }
    
    /**
     * 根据名称搜索用户（带缓存）
     */
    @Cacheable(value = "userSearchCache", key = "#name")
    public List<User> searchUsersByName(String name) {
        simulateSlowService();
        List<User> result = new ArrayList<>();
        for (User user : userDatabase.values()) {
            if (user.getName().toLowerCase().contains(name.toLowerCase())) {
                result.add(user);
            }
        }
        return result;
    }
    
    /**
     * 清除所有用户相关缓存
     */
    @CacheEvict(value = {"userCache", "userListCache", "userSearchCache"}, allEntries = true)
    public void clearAllUserCaches() {
        // 这个方法调用会触发清除所有指定缓存
    }
    
    public static class User {
        private Long id;
        private String name;
        private String email;
        
        public User() {
            // Default constructor for JSON deserialization
        }
        
        public User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    '}';
        }
    }
}
