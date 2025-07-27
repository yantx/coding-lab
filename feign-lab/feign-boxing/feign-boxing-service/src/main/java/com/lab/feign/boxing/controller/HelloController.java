package com.lab.feign.boxing.controller;

import com.lab.feign.boxing.api.HelloApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author:
 * @Date: 2025-04-29 21:12:44
 * @Description:
 */
@RestController
public class HelloController implements HelloApi {

    @GetMapping("/hello")
    public String hello(String name) {
        return "Hello, " + name;
    }

    @GetMapping("/hello2")
    public String hello2(String name) {
        return "Hello2, " + name;
    }

    @GetMapping("/hello3")
    public String hello3(String name) {
        return "Hello3, " + name;
    }

}
