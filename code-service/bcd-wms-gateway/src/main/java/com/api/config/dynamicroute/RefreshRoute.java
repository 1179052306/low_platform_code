package com.api.config.dynamicroute;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.synchronizedMap;

/**
 * @author lw
 * @date: 2024/12/16
 * @description:
 **/
@Component
public class RefreshRoute {
    @Autowired
    private DynamicRouteService dynamicRouteService;
    @Autowired
    GetDataBaseRouteData getDataBaseRouteData;
    //这里是更新路由的策略，大家根据自己的情况来就好，定时刷新
    @Scheduled(cron = "0 0/3 * * * ?")
    @PostConstruct
    public void refreshConfig() throws Exception {


        if(dynamicRouteService.getCount()>0){
            Map<String, RouteDefinition> routes = synchronizedMap(
                    new LinkedHashMap<String, RouteDefinition>());
            List<String> disables= getDataBaseRouteData.getRoute(routes);
            if(disables.size()>0){
                for (int i = 0; i < disables.size(); i++) {
                    dynamicRouteService.delete(disables.get(i)).subscribe();
                }

            }
            if(routes.size()>0){
                Iterator<Map.Entry<String, RouteDefinition>> iter = routes.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, RouteDefinition> entry = iter.next();
                    dynamicRouteService.update(entry.getValue());
                    System.out.println(entry.getKey() + "\t" + entry.getValue());
                }
            }
        }

        
    }


}
