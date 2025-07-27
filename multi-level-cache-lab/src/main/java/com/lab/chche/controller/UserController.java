package com.lab.chche.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lab.chche.cache.MultiLevelCacheManager;
import com.lab.chche.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final MultiLevelCacheManager cacheManager;

    @Autowired
    public UserController(UserService userService, MultiLevelCacheManager cacheManager) {
        this.userService = userService;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        UserService.User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody UserService.User userUpdate) {
        
        // In a real app, you'd want to validate the user exists first
        userUpdate.setId(id);
        UserService.User updatedUser = userService.updateUser(userUpdate);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 获取所有用户列表（带缓存）
     */
    @GetMapping
    public ResponseEntity<List<UserService.User>> getAllUsers() {
        List<UserService.User> users = userService.getAllUsers();
        Cache.ValueWrapper valueWrapper =cacheManager.getCache("userListCache").get("allUsers");
        List<UserService.User> userList = (List<UserService.User>) valueWrapper.get();

        userList.forEach(user -> {
            System.out.println(user);
        });

        return ResponseEntity.ok(users);
    }
    
    /**
     * 根据名称搜索用户（带缓存）
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserService.User>> searchUsers(@RequestParam String name) {
        return ResponseEntity.ok(userService.searchUsersByName(name));
    }
    
    /**
     * 清除所有用户相关缓存
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearUserCaches() {
        userService.clearAllUserCaches();
        return ResponseEntity.noContent().build();
    }
}
