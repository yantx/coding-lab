package com.lab.eureka;

import com.netflix.discovery.EurekaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author:
 * @Date: 2025-03-27 07:31:41
 * @Description:
 */
@RestController
public class GreetingController {

    @Autowired
    @Lazy
    private EurekaClient eurekaClient;
    @Value("${spring.application.name}")
    private String appName;


    @GetMapping("/greeting")
    public String greeting() {
        return String.format(
                "Hello from '%s'!", eurekaClient.getApplication(appName).getName());
    }

    @GetMapping("/sn")
    public Map<String, Object> scientificNotation() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("demo1", 1.00000000001);
        map.put("demo2", 0.00000000001);
        map.put("demo3", 1.22222222223);
        map.put("demo4", 12345674.123);
        map.put("demo5", new BigDecimal("1.00000000001"));
        map.put("demo6", new BigDecimal("0.00000000001"));
        map.put("demo7", new BigDecimal("1.2222222223"));
        map.put("demo8", new BigDecimal(12345674.123));
        System.out.println("demo1: " + map.get("demo1"));
        System.out.println("demo2: " + map.get("demo2"));
        System.out.println("demo3: " + map.get("demo3"));
        System.out.println("demo4: " + map.get("demo4"));

        System.out.println("demo5: " + map.get("demo5"));
        System.out.println("demo6: " + map.get("demo6"));
        System.out.println("demo7: " + map.get("demo7"));
        System.out.println("demo8: " + map.get("demo8"));

        return map;
    }

    public static void main(String[] args) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("demo1", 1.00000000001);
        map.put("demo2", 0.00000000001);
        map.put("demo3", 1.22222222223);
        map.put("demo4", 122222222.223);
        map.put("demo5", new BigDecimal("1.00000000001"));
        map.put("demo6", new BigDecimal("0.00000000001").toPlainString());
        map.put("demo7", new BigDecimal("1.2222222223"));
        map.put("demo8", new BigDecimal("122222222.223"));
        System.out.println("demo1: " + map.get("demo1"));
        System.out.println("demo2: " + map.get("demo2"));
        System.out.println("demo3: " + map.get("demo3"));
        System.out.println("demo4: " + map.get("demo4"));

        System.out.println("demo5: " + map.get("demo5"));
        System.out.println("demo6: " + map.get("demo6"));
        System.out.println("demo7: " + map.get("demo7"));
        System.out.println("demo8: " + map.get("demo8"));
    }
}
