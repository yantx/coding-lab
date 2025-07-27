package com.galio.gateway.filter;

/**
 * @Author:
 * @Date: 2025-06-18 23:45:59
 * @Description:
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter implements GlobalFilter {

 private static final int MAX_LOG_SIZE = 1024; // 1KB 日志限制

 @Override
 public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
  // 生成跟踪号
  String traceNo = UUID.randomUUID().toString();

  // 添加跟踪头
  ServerHttpRequest newRequest = exchange.getRequest().mutate()
          .header("_TRACE_NO", traceNo)
          .build();

  // 使用缓存上下文替代MDC（反应式安全）
  exchange.getAttributes().put("traceNo", traceNo);

  // 选择性记录请求体
  if (exchange.getRequest().getMethod() == HttpMethod.POST) {
   return DataBufferUtils.join(exchange.getRequest().getBody())
           .flatMap(dataBuffer -> {
            // 限制读取大小
            int readSize = Math.min(dataBuffer.readableByteCount(), MAX_LOG_SIZE);
            byte[] bytes = new byte[readSize];
            dataBuffer.read(bytes);

            // 记录摘要日志
            log.debug("请求摘要[{}]: {}",
                    traceNo,
                    new String(bytes, StandardCharsets.UTF_8));
            return chain.filter(exchange.mutate().request(newRequest).build());
           });
  } else {
   // 非POST请求仅记录基础信息
   log.info("请求信息[{}]: {} {}",
           traceNo,
           exchange.getRequest().getMethod(),
           exchange.getRequest().getURI());
   return chain.filter(exchange.mutate().request(newRequest).build());
  }
 }
}
