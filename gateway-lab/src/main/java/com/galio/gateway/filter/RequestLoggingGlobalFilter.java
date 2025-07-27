package com.galio.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);
    private static final String TRACE_NO = "_TRACE_NO";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 生成唯一跟踪号
        String traceNo = UUID.randomUUID().toString();

        // 添加跟踪号到请求头
        ServerHttpRequest newRequest = exchange.getRequest()
                .mutate()
                .header(TRACE_NO, traceNo)
                .build();

        // 添加跟踪号到MDC
        MDC.put("traceNo", traceNo);

        // 根据请求方法处理日志记录
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);
                    logRequestInfo(exchange, traceNo, body);
                    return chain.filter(exchange.mutate().request(newRequest).build());
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    logRequestInfo(exchange, traceNo, "");
                    chain.filter(exchange.mutate().request(newRequest).build());
                }));
    }

    private void logRequestInfo(ServerWebExchange exchange, String traceNo, String body) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (HttpMethod.POST.equals(method)) {
            logger.info("请求信息 ---->全局跟踪号: {}, 请求地址: {}, 请求方法: {}, 请求内容: {}",
                    traceNo, path, method, body);
        } else {
            logger.info("请求信息 ---->全局跟踪号: {}, 请求地址: {}, 请求方法: {}",
                    traceNo, path, method);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 最高优先级
    }
}