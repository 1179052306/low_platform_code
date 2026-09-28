package com.api.config.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 无权限访问异常
 * 用于定义在请求被拒绝访问时的处理逻辑。当用户没有足够的权限访问某个资源时，该处理器将被触发，可以返回适当的错误响应或执行其他操作。
 */
@Slf4j
public class CustomServerAccessDeniedHandler implements ServerAccessDeniedHandler {
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        
        log.error("无权限访问:", denied);
        ServerHttpRequest request = exchange.getRequest();
        return Mono.defer(() -> Mono.just(exchange.getResponse()))
                .flatMap(response -> {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    String body = "{\"code\":403,\"msg\":\"您无权限访问\"}";
                    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                    return response.writeWith(Mono.just(buffer))
                            .doOnError(error -> DataBufferUtils.release(buffer));
                });

    }
}
