package com.galio.gateway.filter;

import com.galio.gateway.utils.WebFluxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 响应日志过滤器
 * 记录响应状态码、响应体内容，并处理MDC跟踪
 */
@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingFilter.class);
    private static final String TRACE_NO_HEADER = "_TRACE_NO";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求信息
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // 包装响应对象，以便读取响应体
        ServerHttpResponse originalResponse = exchange.getResponse();
        
        // 创建响应包装器
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        // 合并所有数据缓冲区
                        StringBuilder sb = new StringBuilder();
                        dataBuffers.forEach(buffer -> {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            DataBufferUtils.release(buffer);
                            String bodyString = new String(bytes, StandardCharsets.UTF_8);
                            sb.append(bodyString);
                        });
                        
                        String responseBody = sb.toString();
                        
                        // 获取响应状态码
                        HttpStatus statusCode = this.getStatusCode();
                        int status = statusCode != null ? statusCode.value() : 0;
                        
                        // 获取跟踪号
                        String traceNo = originalResponse.getHeaders().getFirst(TRACE_NO_HEADER);
                        if (traceNo != null && !traceNo.isEmpty()) {
                            MDC.put(TRACE_NO_HEADER, traceNo);
                        }
                        
                        // 记录日志
                        log.info("请求路径: {}, 响应状态码: {}, 响应体: {}", path, status, responseBody);
                        
                        // 清除MDC
                        if (traceNo != null) {
                            MDC.remove(TRACE_NO_HEADER);
                        }
                        
                        // 返回新的数据缓冲区
                        return originalResponse.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
                    }));
                }
                return super.writeWith(body);
            }
        };
        
        // 继续过滤器链，使用包装后的响应对象
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        // 确保在GlobalLogFilter之后执行
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
