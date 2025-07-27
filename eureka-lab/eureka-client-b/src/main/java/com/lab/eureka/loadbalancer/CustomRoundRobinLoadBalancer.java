package com.lab.eureka.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

@Slf4j
public class CustomRoundRobinLoadBalancer extends RoundRobinLoadBalancer {

    public CustomRoundRobinLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplier, String serviceId) {
        super(supplier, serviceId, -1);
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return super.choose(request).doOnNext(response -> {
            if (response.hasServer()) {
                ServiceInstance instance = response.getServer();
                String instanceInfo = String.format("Service: %s, Instance: %s:%d",
                        instance.getServiceId(),
                        instance.getHost(),
                        instance.getPort());
                ServiceInstanceHolder.set(instanceInfo);

                log.info("LoadBalancer 选择的服务实例: {}", instanceInfo);
                log.info("当前服务实例信息: {}", instanceInfo);
                log.info("服务实例元数据: {}", instance.getMetadata());
            }
        });
    }
}