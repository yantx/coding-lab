package com.lab.eureka.config;

import com.lab.eureka.loadbalancer.CustomRoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

//@Configuration
public class LoadBalancerConfig {

//    @Bean
//    public ReactorServiceInstanceLoadBalancer customLoadBalancer(
//            ObjectProvider<ServiceInstanceListSupplier> provider) {
//        return new CustomRoundRobinLoadBalancer(provider, "eureka-client-b");
//    }
//    @Bean
//    public ReactorServiceInstanceLoadBalancer customLoadBalancer(
//            LoadBalancerClientFactory loadBalancerClientFactory, Environment environment) {
//        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
//        return new CustomRoundRobinLoadBalancer(
//                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
//                name
//        );
//    }


    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(Environment env) {
        // 从配置读取URI列表
        String urisStr = env.getProperty(
                "spring.cloud.loadbalancer.clients.eureka-client-ccc.static-uris"
        );
        List<String> uris = Arrays.asList(urisStr.split(","));
        return new StaticServiceInstanceListSupplier("eureka-client-ccc", uris);
    }
    @Bean
    public ReactorServiceInstanceLoadBalancer customLoadBalancer(
            LoadBalancerClientFactory loadBalancerClientFactory) {
        return new CustomRoundRobinLoadBalancer(
                loadBalancerClientFactory.getLazyProvider("eureka-client-ccc", ServiceInstanceListSupplier.class),
                "eureka-client-ccc"
        );
    }
}