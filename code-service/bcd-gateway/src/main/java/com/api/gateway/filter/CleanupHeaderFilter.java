package com.api.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 清洗外部伪造的内部头（最高优先级），再由 AuthGlobalFilter 注入可信值。
 */
@Component
public class CleanupHeaderFilter implements GlobalFilter, Ordered {

    public static final String[] TRUSTED_HEADERS = {
            "X-User-Id", "X-User-Name", "X-Role-Codes", "X-Device-Id", "X-Gw-Sign", "X-Gw-Time"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> remove = new ArrayList<>();
        for (String h : TRUSTED_HEADERS) {
            if (exchange.getRequest().getHeaders().containsKey(h)) {
                remove.add(h);
            }
        }
        if (!remove.isEmpty()) {
            exchange = exchange.mutate()
                    .request(r -> r.headers(hs -> {
                        for (String h : remove) {
                            hs.remove(h);
                        }
                    }))
                    .build();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
