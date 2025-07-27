package com.lab.eureka;

import com.lab.eureka.loadbalancer.ServiceInstanceHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @Author:
 * @Date: 2025-07-10 10:38:54
 * @Description:
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SomeService {

    private final RestTemplate restTemplate;

    public String callService() {
        // 这里会触发负载均衡

        String url = "http://eureka-client-ccc/hello";
        System.out.println(url);
        String result = restTemplate.getForObject(url, String.class);
        System.out.println(result);
        // 获取当前请求的服务实例信息
        String currentInstance = ServiceInstanceHolder.get();
        log.info("Current service instance: {}", currentInstance);
        ServiceInstanceHolder.remove();

        return result;
    }
}
