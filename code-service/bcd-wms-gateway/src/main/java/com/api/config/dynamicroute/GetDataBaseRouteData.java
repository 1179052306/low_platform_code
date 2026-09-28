package com.api.config.dynamicroute;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.DbHelp;
import com.server.db.Tenant;
import com.server.generate.GenerateSqlImpl;
import com.server.tenant.TenantData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * @author lw
 * @date: 2024/12/16
 * @description:
 **/
@Slf4j
@Service
public class GetDataBaseRouteData {
    @Resource
    private DbHelp dbHelp;
    @Resource
    private GenerateSqlImpl generateSql;

    @Autowired
    Tenant tenant;

    @Autowired
    TenantData tenantData;

    @Value("${app.prefix:}")
    private String appPrefix;

    @Value("${app.env:}")
    private String appEnv;

    public List<String> getRoute(Map<String, RouteDefinition> routes) throws Exception {
        JSONArray routeArray = new JSONArray();
        JSONArray predicatesArray = new JSONArray();
        if (tenant.getSingle()) {
            JSONObject data = this.getSingleData();
            routeArray = data.getJSONArray("S_GATEWAY_ROUTE");
            predicatesArray = data.getJSONArray("S_GATEWAY_PREDICATES");
        }else {
            JSONObject data =  tenantData.getTenantRouteData();
            if(data.getJSONArray("M_TENANT_ROUTE")!=null){
                routeArray = data.getJSONArray("M_TENANT_ROUTE");
            }
            if(data.getJSONArray("M_TENANT_ROUTE_PREDICATES")!=null){
                predicatesArray = data.getJSONArray("M_TENANT_ROUTE_PREDICATES");
            }
        }
        List<String> disables = new ArrayList<>();
        if (routeArray.size() > 0) {

            for (int i = 0; i < routeArray.size(); i++) {
                JSONObject row = routeArray.getJSONObject(i);
                if (row.getString("ACTIVE").equals("1")) {
                    this.setRoute(routes, row, predicatesArray, disables);
                } else {

                    JSONArray disable = new JSONArray(predicatesArray.stream().filter(item -> ((JSONObject) item).getString("ROUTE_KEY").equals(row.getString("ROUTE_KEY"))).collect(Collectors.toList()));
                    for (int t = 0; t < disable.size(); t++) {
                        disables.add(disable.getJSONObject(t).getString("ROUTE_PREDICATES_ID"));
                    }
                }

            }

        }
        return disables;

    }


    public JSONObject getSingleData() throws Exception {
        List<GenerateSqlTran> sqlTranList = new ArrayList<>();
        GenerateSqlTran mst = new GenerateSqlTran("SELECT * FROM S_GATEWAY_ROUTE T WHERE 1=1  ORDER BY T.SORT_INDEX ASC");
        mst.setTableName("S_GATEWAY_ROUTE");
        sqlTranList.add(mst);

        GenerateSqlTran predicates = new GenerateSqlTran("SELECT * FROM S_GATEWAY_PREDICATES T WHERE 1=1  ORDER BY T.SORT_INDEX ASC");
        predicates.setTableName("S_GATEWAY_PREDICATES");
        sqlTranList.add(predicates);

         return  dbHelp.queryTranJsonObject("project", sqlTranList);
    }




    private void setRoute(Map<String, RouteDefinition> routes, JSONObject mst, JSONArray predicatesArray, List<String> disables) {
        JSONArray predicates = new JSONArray(predicatesArray.stream().filter(item -> ((JSONObject) item).getString("ROUTE_KEY").equals(mst.getString("ROUTE_KEY"))).collect(Collectors.toList()));

        for (int i = 0; i < predicates.size(); i++) {
            JSONObject row = predicates.getJSONObject(i);
            if (row.getString("ACTIVE").equals("1")) {
                RouteDefinition routeDef = new RouteDefinition();
                routeDef.setId(row.getString("ROUTE_PREDICATES_ID"));
                routeDef.setUri(URI.create(appendEnvSuffix(mst.getString("ROUTE_URI"))));
                List<PredicateDefinition> predicateDefinitions = new ArrayList<>();
                PredicateDefinition definition = new PredicateDefinition(row.getString("ROUTE_PREDICATES"));
                predicateDefinitions.add(definition);
                if (!StringUtils.isBlank(mst.getString("ORG_KEY"))) {
                    PredicateDefinition org = new PredicateDefinition("Header=org, ^" + mst.getString("ORG_KEY") + "$");
                    predicateDefinitions.add(org);
                }
                routeDef.setPredicates(predicateDefinitions);
                if (!row.getString("ROUTE_PREDICATES_ID").contains("socket")) {

                    setFilters(routeDef);
                } else {
                    setSocketFilters(routeDef);
                }
                routeDef.setOrder(mst.getInteger("ORDER_INDEX"));
                routes.put(row.getString("ROUTE_PREDICATES_ID"), routeDef);
            } else {
                disables.add(row.getString("ROUTE_PREDICATES_ID"));
            }
        }
    }

    private void setFilters(RouteDefinition routeDef) {

        List<FilterDefinition> filterDefinitions = new ArrayList<>();
        FilterDefinition definition = new FilterDefinition("StripPrefix=1");
        filterDefinitions.add(definition);
        routeDef.setFilters(filterDefinitions);
    }

    private void setSocketFilters(RouteDefinition routeDef) {

        List<FilterDefinition> filterDefinitions = new ArrayList<>();
        FilterDefinition definition = new FilterDefinition();
        definition.setName("AddRequestHeader");
        definition.addArg("name", "Upgrade");
        definition.addArg("value", "websocket");
        filterDefinitions.add(definition);


        FilterDefinition definition1 = new FilterDefinition();
        definition1.setName("AddRequestHeader");
        definition1.addArg("name", "Connection");
        definition1.addArg("value", "upgrade");
        filterDefinitions.add(definition1);
        routeDef.setFilters(filterDefinitions);
    }

    /**
     * 为 lb:// 协议的 URI 拼接 app.prefix + app.env 后缀。
     * 仅对 lb:// 生效（http/https/ws 等不拼），且避免重复添加。
     */
    private String appendEnvSuffix(String uri) {
        if (uri == null || !uri.startsWith("lb://")) {
            return uri;
        }
        String suffix = (appPrefix == null ? "" : appPrefix) + (appEnv == null ? "" : appEnv);
        if (suffix.isEmpty() || uri.contains(suffix)) {
            return uri;
        }
        return uri + suffix;
    }
}
