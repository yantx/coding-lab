package com.lab.feign.boxing.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @Author:
 * @Date: 2025-04-29 21:12:44
 * @Description:
 */
@RequestMapping("/feignHello1")
@FeignClient(name = "feign-boxing-service")
public interface HelloApi {

    @RequestMapping(path = "/hello", method = RequestMethod.GET)
    String hello(@RequestParam(name = "name") String name);


    @RequestMapping("/hello2")
    String hello2(String name);


    @RequestMapping("/hello3")
    String hello3(String name);

}
