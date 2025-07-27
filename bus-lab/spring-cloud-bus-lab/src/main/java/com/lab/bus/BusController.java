package com.lab.bus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bus")
public class BusController {

    @Autowired
    private BusEventPublisher publisher;

    @PostMapping("/refresh")
    public String refresh(@RequestParam String origin, @RequestParam String dest) {
        publisher.publishRefresh(origin, dest);
        return "Bus refresh event sent!";
    }

    /**
     * 发布自定义事件
     * @param origin 事件源服务
     * @param dest 目标服务(*表示所有服务, 或指定服务ID)
     * @param message 事件消息
     * @param data 事件数据(JSON格式)
     * @return 操作结果
     */
    @PostMapping("/publish-event")
    public Map<String, Object> publishCustomEvent(
            @RequestParam(required = false, defaultValue = "${spring.cloud.bus.id}") String origin,
            @RequestParam(required = false, defaultValue = "*") String dest,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "{}") String data) {
        
        publisher.publishCustomEvent(origin, dest, message, data);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Custom event published successfully");
        result.put("origin", origin);
        result.put("destination", dest);
        result.put("eventType", "CustomBusEvent");
        return result;
    }
}