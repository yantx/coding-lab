package com.lab.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author:
 * @Date: 2025-03-27 07:31:41
 * @Description:
 */
@RestController
public class HelloController {

    @Autowired
    @Lazy
    private EurekaClient eurekaClient;
    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    private SomeService someService;


    @GetMapping("/hello")
    public String hello() {
        return String.format(
                "Hello from '%s'!", eurekaClient.getApplication(appName).getName());
    }

    @GetMapping("/loadBalance")
    public String loadBalance() {
        return someService.callService();
    }


    @GetMapping("/getGreetingNoFeign")
    public String greetingNoFeign() {
        InstanceInfo service = eurekaClient
                .getApplication("eureka-client-a")
                .getInstances()
                .get(0);

        String hostName = service.getHostName();
        int port = service.getPort();

        String url = String.format("http://%s:%d/greeting", hostName, port);
        System.out.println(url);
        RestTemplate restTemplate = new RestTemplate();
        String result = restTemplate.getForObject(url, String.class);
        System.out.println(result);

        return String.format(
                "Hello from '%s'!", eurekaClient.getApplication(appName).getName());
    }


    @GetMapping("/scientificNotationNoFeign")
    public Map<String, Object> scientificNotationNoFeign() {
        InstanceInfo service = eurekaClient
                .getApplication("eureka-client-a")
                .getInstances()
                .get(0);

        String hostName = service.getHostName();
        int port = service.getPort();

        String url = String.format("http://%s:%d/sn", hostName, port);
        System.out.println(url);
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> result = restTemplate.getForObject(url, HashMap.class);
        System.out.println(result);

        return result;
    }
}
