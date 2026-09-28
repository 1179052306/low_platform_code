package com.api.config.dynamicroute;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import static java.util.Collections.synchronizedMap;

/**
 * @author lw
 * @date: 2024/12/16
 * @description:
 **/
@Repository
public class BindDataBaseRouteDefinitionRepository implements RouteDefinitionRepository {
    public Map<String, RouteDefinition> getRoutes() {
        return routes;
    }

    private final Map<String, RouteDefinition> routes = synchronizedMap(
            new LinkedHashMap<String, RouteDefinition>());


    @Autowired
    GetDataBaseRouteData getDataBaseRouteData;
    
    @SneakyThrows
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {


        if(routes.size()==0) {
            getDataBaseRouteData.getRoute(routes);
        }
        return Flux.fromIterable(routes.values());
    }



    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(r -> {
            if (StringUtils.isEmpty(r.getId())) {
                return Mono.error(new IllegalArgumentException("id may not be empty"));
            }
            routes.put(r.getId(), r);
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            if (routes.containsKey(id)) {
                routes.remove(id);
                return Mono.empty();
            }
            return Mono.empty();
        });

    }

}
