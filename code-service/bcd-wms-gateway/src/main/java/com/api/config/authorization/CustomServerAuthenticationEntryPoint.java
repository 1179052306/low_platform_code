package com.api.config.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 认证失败异常处理
 * 用于定义在需要身份验证但未提供有效凭据时的处理逻辑。当用户尝试访问需要身份验证的资源但没有提供有效的凭据时，该入口点将被触发，通常返回一个要求用户进行身份验证的响应。
 */
@Slf4j
public class CustomServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
            log.error("发生了认证异常", ex);

        String request_type = exchange.getRequest().getHeaders().getFirst("request_type");
        String requestType = exchange.getRequest().getHeaders().getFirst("requestType");
        return Mono.defer(() -> Mono.just(exchange.getResponse()))
                .flatMap(response -> {
                    if(request_type!=null){
                        if(request_type.equals("1")||request_type.equals("2")){
                            if(((OAuth2AuthenticationException) ex).getError().getErrorCode().equals("426")){
                                response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
                                String body = "{\"code\":"+((OAuth2AuthenticationException) ex).getError().getErrorCode()+",\"msg\":"+((OAuth2AuthenticationException) ex).getError().getDescription()+"}";
                                DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                                return response.writeWith(Mono.just(buffer))
                                        .doOnError(error -> DataBufferUtils.release(buffer));
                            }
                        }
                        if(requestType!=null) {
                            if(requestType.equals("1")||requestType.equals("2")){
                                if (((OAuth2AuthenticationException) ex).getError().getErrorCode().equals("426")) {
                                    response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
                                    String body = "{\"code\":" + ((OAuth2AuthenticationException) ex).getError().getErrorCode() + ",\"msg\":" + ((OAuth2AuthenticationException) ex).getError().getDescription() + "}";
                                    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                                    return response.writeWith(Mono.just(buffer))
                                            .doOnError(error -> DataBufferUtils.release(buffer));
                                }
                            }
                        }
                    }else{
                        if(requestType!=null) {
                            if(requestType.equals("1")||requestType.equals("2")){
                                if (((OAuth2AuthenticationException) ex).getError().getErrorCode().equals("426")) {
                                    response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
                                    String body = "{\"code\":" + ((OAuth2AuthenticationException) ex).getError().getErrorCode() + ",\"msg\":" + ((OAuth2AuthenticationException) ex).getError().getDescription() + "}";
                                    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                                    return response.writeWith(Mono.just(buffer))
                                            .doOnError(error -> DataBufferUtils.release(buffer));
                                }
                            }
                        }
                    }

                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    String body = "{\"code\":401,\"msg\":\"token不合法或过期\"}";
                    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                    return response.writeWith(Mono.just(buffer))
                            .doOnError(error -> DataBufferUtils.release(buffer));
                });
    }
}
