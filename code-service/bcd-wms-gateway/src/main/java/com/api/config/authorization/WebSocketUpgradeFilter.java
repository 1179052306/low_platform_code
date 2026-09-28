package com.api.config.authorization;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * @author lw
 * @date: 2025/1/16
 * @description:
 **/
@Component
public class WebSocketUpgradeFilter extends AbstractGatewayFilterFactory<WebSocketUpgradeFilter.Config> {

    public WebSocketUpgradeFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("Connection", "Upgrade")
                    .header("Upgrade", "websocket")
                    .build();

            return chain.filter(exchange.mutate().request(request).build());
        };
    }

    public static class Config {
        // 这里可以添加配置属性
    }
}