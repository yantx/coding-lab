package com.galio.gateway.filter;

import com.galio.gateway.utils.WebFluxUtils;
import lombok.extern.slf4j.Slf4j;
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
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class GlobalLogFilter implements GlobalFilter, Ordered {

    private static final String TRACE_NO = "_TRACE_NO";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceNo = UUID.randomUUID().toString();
        ServerHttpRequest newRequest = exchange.getRequest().mutate().header(TRACE_NO, traceNo).build();
        MDC.put("traceNo", traceNo);

        // 使用WebFluxUtils获取原始请求路径
        String path = WebFluxUtils.getOriginalRequestUrl(exchange);
        log.info("请求信息 ---->全局跟踪号: {}, 请求地址: {}, 请求方法: {}, 请求头: {}",
                traceNo, path, newRequest.getMethod(), newRequest.getHeaders());

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return super.writeWith(DataBufferUtils.join(body).flatMap(dataBuffer -> {
                    byte[] contentBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(contentBytes);
                    DataBufferUtils.release(dataBuffer);
                    String responseBody = new String(contentBytes, StandardCharsets.UTF_8);

                    HttpStatus status = getStatusCode();
                    log.info("响应信息 ---->全局跟踪号: {}, 响应状态: {}, 响应内容: {}",
                            traceNo, status != null ? status.value() : 0, responseBody);

                    return Mono.just(getDelegate().bufferFactory().wrap(contentBytes));
                }));
            }
        };

        decoratedResponse.getHeaders().add(TRACE_NO, traceNo);

        return chain.filter(exchange.mutate().request(newRequest).response(decoratedResponse).build())
                .doFinally(signalType -> MDC.remove("traceNo"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
//package com.galio.gateway.filter;
//
//import lombok.extern.slf4j.Slf4j;
//import org.slf4j.MDC;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//@Slf4j
//@Component
//public class GlobalLogFilter implements GlobalFilter, Ordered {
//
//    private static final String TRACE_NO = "_TRACE_NO";
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        // 生成traceNo并放入请求头和MDC
//        String traceNo = UUID.randomUUID().toString();
//        ServerHttpRequest newRequest = exchange.getRequest().mutate().header(TRACE_NO, traceNo).build();
//        MDC.put("traceNo", traceNo);
//
//        // 记录请求日志
//        log.info("请求信息 ---->全局跟踪号: {}, 请求地址: {}, 请求方法: {}, 请求头: {}",
//                traceNo, newRequest.getURI().getPath(), newRequest.getMethod(), newRequest.getHeaders());
//
//        ServerHttpResponse originalResponse = exchange.getResponse();
//        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
//            @Override
//            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
//                return super.writeWith(DataBufferUtils.join(body).flatMap(dataBuffer -> {
//                    byte[] contentBytes = new byte[dataBuffer.readableByteCount()];
//                    dataBuffer.read(contentBytes);
//                    String responseBody = new String(contentBytes, StandardCharsets.UTF_8);
//
//                    // 记录响应日志
//                    HttpStatus status = getStatusCode();
//                    log.info("响应信息 ---->全局跟踪号: {}, 响应状态: {}, 响应内容: {}",
//                            traceNo, status != null ? status.value() : 0, responseBody);
//
//                    // 重新写回响应体
//                    return Mono.just(getDelegate().bufferFactory().wrap(contentBytes));
//                }));
//            }
//        };
//
//        // 响应头加traceNo
//        decoratedResponse.getHeaders().add(TRACE_NO, traceNo);
//
//        return chain.filter(exchange.mutate().request(newRequest).response(decoratedResponse).build())
//                .doFinally(signalType -> MDC.remove("traceNo"));
//    }
//
//    @Override
//    public int getOrder() {
//        return Ordered.HIGHEST_PRECEDENCE;
//    }
//}