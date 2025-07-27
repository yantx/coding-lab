package com.lab.eureka.feginclient;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * @Author:
 * @Date: 2025-07-02 22:01:41
 * @Description:
 */
@FeignClient("eureka-client-b")
public interface TestFeignClient {
}
