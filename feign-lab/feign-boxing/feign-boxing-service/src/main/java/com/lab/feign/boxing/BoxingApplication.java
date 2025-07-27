package com.lab.feign.boxing;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * @Author:
 * @Date: 2025-04-29 21:11:43
 * @Description:
 */
@EnableEurekaClient
@SpringBootApplication
public class BoxingApplication {


    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(BoxingApplication.class, args);
        System.out.println("Hello, BoxingApplication!");
    }

}
