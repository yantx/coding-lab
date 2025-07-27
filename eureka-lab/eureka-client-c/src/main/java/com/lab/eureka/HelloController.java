package com.lab.eureka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author:
 * @Date: 2025-03-27 07:31:41
 * @Description:
 */
@RestController
public class HelloController {

    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/hello")
    public String hello() {
        return "hello from " + appName;
    }

}
