package com.lab.bus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;

/**
 * @Author:
 * @Date: 2025-06-23 23:18:45
 * @Description:
 */
@SpringBootApplication
@RemoteApplicationEventScan(basePackages = "com.lab.bus.event")
public class BusApplicationMain {

  public static void main(String[] args) {
    SpringApplication.run(BusApplicationMain.class, args);
  }
}

