package com.lab.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author:
 * @Date: 2025-03-27 07:28:16
 * @Description:
 */
@SpringBootApplication
public class ClientCApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientCApplication.class, args);
        System.out.println("Hello, ClientBApplication!");
    }

}
