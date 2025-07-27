package com.lab.eureka;

import com.lab.eureka.config.LoadBalancerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * @Author:
 * @Date: 2025-03-27 07:28:16
 * @Description:
 */
//@LoadBalancerClients(defaultConfiguration = LoadBalancerConfig.class)
@SpringBootApplication
@EnableEurekaClient
public class ClientBApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientBApplication.class, args);
        System.out.println("Hello, ClientBApplication!");
    }

}
