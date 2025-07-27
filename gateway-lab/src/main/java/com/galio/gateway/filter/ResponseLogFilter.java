package com.galio.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * @Author:
 * @Date: 2025-06-18 23:43:04
 * @Description:
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class ResponseLogFilter implements GlobalFilter {

    private static final int MAX_LOG_SIZE = 2048; // 2KB 日志限制

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取缓存中的跟踪号
        String traceNo = exchange.getAttribute("traceNo");
        ServerHttpResponse originalResponse = exchange.getResponse();

        // 创建高效响应装饰器
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return super.writeWith(Flux.from(body).map(dataBuffer -> {
                    // 限制日志大小
                    int readable = Math.min(dataBuffer.readableByteCount(), MAX_LOG_SIZE);
                    byte[] bytes = new byte[readable];
                    dataBuffer.read(bytes);

                    // 记录摘要日志（异步执行）
                    log.debug("响应摘要[{}]: {}",
                            traceNo,
                            new String(bytes, StandardCharsets.UTF_8));
                    return dataBuffer;
                }));
            }
        };

        // 添加跟踪号到响应头
        decoratedResponse.getHeaders().add("_TRACE_NO", traceNo);

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}
