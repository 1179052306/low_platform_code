package com.security.oauth2.config;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.Tenant;
import com.server.tenant_http.TenantHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class DynamicClientDetailsService implements ClientDetailsService {

    @Autowired
    private DynamicDataSourceRouter dataSourceRouter; // 自定义动态数据源切换逻辑
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    Tenant tenant;
//    @Override
//    public ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
//        BaseClientDetails clientDetails = new BaseClientDetails();
//        clientDetails.setClientId("BCD");
//        clientDetails.setClientSecret("e10adc3949ba59abbe56e057f20f883e");
//        Collection<String> scope=new HashSet<>();
//        scope.add("all");
//        clientDetails.setScope(scope);;
//
//        Collection<String> authorizedGrantTypes =new HashSet<>();
//
//        String[] grants= "authorization_code,client_credentials,refresh_token".split(",");
//
//        for (int i = 0; i < grants.length; i++) {
//            authorizedGrantTypes.add(grants[i]);
//        }
//
//        clientDetails.setAuthorizedGrantTypes(authorizedGrantTypes);
//        Set<String> registeredRedirectUris=new HashSet<>();
//        registeredRedirectUris.add( "http://www.baidu.com");
//        clientDetails.setRegisteredRedirectUri(registeredRedirectUris);
////
//        return clientDetails;
//
//    }
    @Override
    public ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
        if (tenant.getSingle()) {
            try {
                DataSource dataSource = dataSourceRouter.getDataSource("project");
                JdbcClientDetailsService jdbcClientDetailsService = new JdbcClientDetailsService(dataSource);
                jdbcClientDetailsService.setPasswordEncoder(passwordEncoder);
                // 返回对应的客户端详情
                return jdbcClientDetailsService.loadClientByClientId(clientId);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load client details for client id: " + clientId);
            }

        } else {
            log.info("准备获取平台租户授权数据");
            log.info("请求地址：" + this.tenant.getTenanturl() + "/auth/checkClient");
            TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl() + "/auth/checkClient", true);
            try {
                tenantHttp.addParameter("SYSTEM_TYPE",tenant.getSystemType());
                tenantHttp.addParameter("clientId", clientId);
                log.info("发送请求");
                tenantHttp.post();
                log.info("请求结果：" + tenantHttp.getContent());
                JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
                if (!returnData.getBoolean("res")) {
                    log.error("返回结果失败：" + returnData.getString("data"));
                    throw new RuntimeException("Failed to load client details for client id: " + clientId);
                }
                JSONArray data = JSONArray.parseArray(returnData.getString("data"));
                if (data.size() == 0) {
                    log.error("返回数据为空：" + returnData.getString("data"));
                    throw new RuntimeException("Failed to load client details for client id: " + clientId);
                }
                JSONObject clientData=data.getJSONObject(0);
                BaseClientDetails clientDetails = new BaseClientDetails();
                clientDetails.setClientId(clientData.getString("CLIENT_ID"));
                clientDetails.setClientSecret(clientData.getString("CLIENT_SECRET"));
                Collection<String> scope=new HashSet<>();
                scope.add(clientData.getString("SCOPE"));
                clientDetails.setScope(scope);;

                Collection<String> authorizedGrantTypes =new HashSet<>();

                String[] grants= clientData.getString("AUTHORIZED_GRANT_TYPES").split(",");

                for (int i = 0; i < grants.length; i++) {
                    authorizedGrantTypes.add(grants[i]);
                }

                clientDetails.setAuthorizedGrantTypes(authorizedGrantTypes);
                Set<String> registeredRedirectUris=new HashSet<>();
                registeredRedirectUris.add(  clientData.getString("WEB_SERVER_REDIRECT_URI"));
                clientDetails.setRegisteredRedirectUri(registeredRedirectUris);
//
                return clientDetails;
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load client details for client id: " + clientId);
            }
        }

    }
}








