package com.lab.bus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;

/**
 * @Author:
 * @Date: 2025-06-23 23:18:45
 * @Description:
 */
@SpringBootApplication
@RemoteApplicationEventScan(basePackages = "com.lab.bus.event")
public class BusApplicationConsumerMain {

  public static void main(String[] args) {
    SpringApplication.run(BusApplicationConsumerMain.class, args);
  }
}
