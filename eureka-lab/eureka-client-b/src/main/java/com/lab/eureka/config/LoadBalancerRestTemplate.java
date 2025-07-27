package com.lab.eureka.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @Author:
 * @Date: 2025-07-10 11:31:06
 * @Description:
 */
@Configuration
public class LoadBalancerRestTemplate {

 @Bean
 @LoadBalanced
 public RestTemplate restTemplate() {
  return new RestTemplate();
 }

}
