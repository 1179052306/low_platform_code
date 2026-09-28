package com.api;

import com.api.model.GatewayThresholdMillis;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class GlobalLogFilter implements GlobalFilter, Ordered {


    @Autowired
    GatewayThresholdMillis gatewayThresholdMillis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 生成唯一的链路ID，用于追踪
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        // 2. 记录请求开始时间和基本信息
        long startTime = System.currentTimeMillis();
        String requestPath = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethodValue();
        String requestId = exchange.getRequest().getId();

        log.info("🚀 [{}] 收到请求: {} {} | RequestId: {}", traceId, method, requestPath, requestId);

        // 3. 执行请求转发，并处理后续逻辑
        return chain.filter(exchange).then(
                Mono.fromRunnable(() -> {
                    // 4. 请求处理完成后执行
                    long endTime = System.currentTimeMillis();
                    long costTime = endTime - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null ?
                            exchange.getResponse().getStatusCode().value() : 0;

                    // 5. 获取最终路由到的目标服务信息
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String targetUri = (route != null && route.getUri() != null) ? route.getUri().toString() : "未知";
                    long threshold = gatewayThresholdMillis.getGatewayThresholdMillis();
                    if (costTime > threshold) {
                        // 超过阈值，输出 ERROR 日志
                        log.error("⚠️ [SLOW REQUEST] 耗时过长: {} {} | 耗时: {}ms (阈值: {}ms) | 状态码: {} | 目标: {} | TraceId: {}",
                                method, requestPath, costTime, threshold, statusCode, targetUri, traceId);
                    } else {
                        // 正常请求，输出 INFO 日志
                        log.info("✅ [{}] 请求完成: {} {} | 耗时: {}ms | 状态码: {} | 目标: {}",
                                traceId, method, requestPath, costTime, statusCode, targetUri);
                    }
                })
        ).doOnError(throwable -> {
            // 6. 捕获转发过程中的异常
            log.error("❌ [{}] 请求处理异常: {} {} | 错误: {}",
                    traceId, method, requestPath, throwable.getMessage(), throwable);
        }).doFinally(signalType -> {
            // 7. 清理 MDC，防止内存泄漏
            MDC.clear();
        }).then();
    }

    @Override
    public int getOrder() {
        // 设置较高的优先级，确保在其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}