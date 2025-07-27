package com.galio.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;

/**
 * 请求日志过滤器
 * 记录请求信息并处理MDC跟踪
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String TRACE_NO_HEADER = "_TRACE_NO";
    private static final String START_TIME = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String method = request.getMethodValue();
        String path = uri.getPath();
        
        // 记录请求开始时间
        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
        
        // 获取并设置跟踪号
        String traceNo = request.getHeaders().getFirst(TRACE_NO_HEADER);
        if (traceNo != null && !traceNo.isEmpty()) {
            MDC.put(TRACE_NO_HEADER, traceNo);
        }
        
        // 记录请求信息
        log.info("请求方法: {}, 请求路径: {}", method, path);
        
        // 记录查询参数
        if (!request.getQueryParams().isEmpty()) {
            log.info("请求参数: {}", request.getQueryParams());
        }
        
        // 记录请求头
        if (!request.getHeaders().isEmpty()) {
            log.info("请求头: {}", request.getHeaders());
        }
        
        // 继续过滤器链
        return chain.filter(exchange).doFinally(signalType -> {
            // 清除MDC
            MDC.remove(TRACE_NO_HEADER);
            
            // 记录请求耗时
            Long startTime = exchange.getAttribute(START_TIME);
            if (startTime != null) {
                long executeTime = System.currentTimeMillis() - startTime;
                log.info("请求处理完成: {} {}, 耗时: {}ms", method, path, executeTime);
            }
        });
    }

    @Override
    public int getOrder() {
        // 设置为高优先级，确保在其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE + 1000;
    }
}
