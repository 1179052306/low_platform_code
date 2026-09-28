package com.server.tenant;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.Tenant;
import com.server.tenant_http.TenantHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author lw
 * @date: 2025/3/4
 * @description:
 **/
@Slf4j
@Service
public class TenantData {

    @Autowired
    Tenant tenant;

    public JSONArray getTenant() {
        JSONArray data = new JSONArray();
        log.info("准备获取平台租户数据");
        log.info("请求地址：" + this.tenant.getTenanturl() + "/tenant/getTenant");
        TenantHttp httpClients = new TenantHttp(tenant.getTenanturl() + "/tenant/getTenant", false);
        log.info("发送请求");
        try {
            httpClients.addParameter("SYSTEM_TYPE",tenant.getSystemType());

            httpClients.post();
            log.info("请求结果：" + httpClients.getContent());
            JSONObject returnData = JSONObject.parseObject(httpClients.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
                return data;
            }
            data = JSONArray.parseArray(returnData.getString("data"));
        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }
        return data;
    }
    private static String route_version = "null";

    private static String white_version = "null";

    public JSONObject getTenantRouteData() throws Exception {
        JSONObject data=new JSONObject();
        String version = getReleaseVersion("ROUTE_VERSION");
        if (route_version.equals(version)) {
            return data;
        }
        route_version = version;
        log.info("准备获取平台路由数据");
        log.info("请求地址：" + this.tenant.getTenanturl()+"/tenant/getTenantRouteData");
        TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl()+"/tenant/getTenantRouteData", true);
        try {
            tenantHttp.addParameter("SYSTEM_TYPE",tenant.getSystemType());
            log.info("发送请求");
            tenantHttp.post();
            log.info("请求结果：" + tenantHttp.getContent());
            JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
                return data;
            }
            data = JSONObject.parseObject(returnData.getString("data"));

        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }
        return data;
    }

    public JSONArray getWhiteData() throws Exception {
        JSONArray data=new JSONArray();
        String version = getReleaseVersion("WHITE_VERSION");
        if (white_version.equals(version)) {
            return data;
        }
        white_version = version;
        log.info("准备获取平台路由数据");
        log.info("请求地址：" + this.tenant.getTenanturl()+"/tenant/getTenantWhiteData");
        TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl()+"/tenant/getTenantWhiteData", true);
        try {
            tenantHttp.addParameter("SYSTEM_TYPE",tenant.getSystemType());
            log.info("发送请求");
            tenantHttp.post();
            log.info("请求结果：" + tenantHttp.getContent());
            JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
                return data;
            }
            data = JSONArray.parseArray(returnData.getString("data"));

        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }
        return data;
    }

    public String getReleaseVersion(String name) {

        String version = "null";
        log.info("准备获取平台租户发布版本");
        log.info("请求地址：" + this.tenant.getTenanturl() + "/tenant/getReleaseVersion");
        TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl() + "/tenant/getReleaseVersion", true);
        try {
            tenantHttp.addParameter("SYSTEM_TYPE",tenant.getSystemType());
            log.info("发送请求");
            tenantHttp.post();
            log.info("请求结果：" + tenantHttp.getContent());
            JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
            }
            version = returnData.getJSONObject("data").getString(name);
        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }

        return version;
    }
}
