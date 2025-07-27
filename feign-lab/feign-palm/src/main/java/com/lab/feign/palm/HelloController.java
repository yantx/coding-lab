package com.lab.feign.palm;

import com.lab.feign.boxing.api.HelloApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author:
 * @Date: 2025-03-27 07:31:41
 * @Description:
 */
@RestController
public class HelloController {

    @Autowired
    private HelloApi helloApi;


    @GetMapping("boxingHello")
    public String hello(String name) {
        return helloApi.hello(name);
    }

}
