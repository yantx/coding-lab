package com.lab.eureka.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public class StaticServiceInstanceListSupplier implements ServiceInstanceListSupplier {
 private final String serviceId;
 private final List<String> uris;

 public StaticServiceInstanceListSupplier(String serviceId, List<String> uris) {
  this.serviceId = serviceId;
  this.uris = uris;
 }

 @Override
 public String getServiceId() {
  return serviceId;
 }

 @Override
 public Flux<List<ServiceInstance>> get() {
  List<ServiceInstance> instances = uris.stream()
          .map(uri -> new DefaultServiceInstance(
                  serviceId + "-" + uri.hashCode(), // 唯一实例ID
                  serviceId,
                  URI.create(uri).getHost(),
                  URI.create(uri).getPort(),
                  false
          ))
          .collect(Collectors.toList());
  return Flux.just(instances);
 }
}
