package com.lab.feign.palm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @Author:
 * @Date: 2025-03-27 07:28:16
 * @Description:
 */
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients(basePackages = "com.lab.feign.boxing.api") // 接口所在包路径
public class PalmApplication {

    public static void main(String[] args) {
        SpringApplication.run(PalmApplication.class, args);
        System.out.println("Hello, PalmApplication!");
    }

}
