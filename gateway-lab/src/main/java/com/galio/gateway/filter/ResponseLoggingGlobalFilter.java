package com.galio.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ResponseLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_NO = "_TRACE_NO";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        // 创建自定义响应包装器
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return super.writeWith(DataBufferUtils.join(body).flatMap(dataBuffer -> {
                    // 读取响应内容
                    byte[] contentBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(contentBytes);
                    String responseBody = new String(contentBytes, StandardCharsets.UTF_8);

                    // 处理跟踪号
                    String traceNoHeader = originalResponse.getHeaders().getFirst(TRACE_NO);
                    if (traceNoHeader != null && !traceNoHeader.isEmpty()) {
                        MDC.put("traceNo", traceNoHeader);
                    }

                    // 记录响应日志
                    logResponseInfo(exchange, originalResponse.getStatusCode(), responseBody);

                    // 重新构造响应体
                    byte[] newBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    return Mono.just(exchange.getResponse().bufferFactory().wrap(newBytes));
                }));
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private void logResponseInfo(ServerWebExchange exchange, HttpStatus status, String body) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("响应信息 ---->请求地址: {}, 响应状态: {}, 响应内容: {}", path, status.value(), body);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // 最低优先级
    }
}