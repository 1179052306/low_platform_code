package com.server.db;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.tenant_http.TenantHttp;
import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * @author lw
 * @date: 2025/3/13
 * @description:
 **/
@Service
@Slf4j
public class LoadMultiDataSource {

    @Autowired
    private DBProperties dbProperties;


    @Autowired
    HikariConfigProperties hikariConfig;
    @Autowired
    Tenant tenant;


    private static String release_version = "null";

    public MultiDataSource loadMultiDataSource() {
        MultiDataSource multiSource = new MultiDataSource();
        multiSource.setTargetDataSources(loadMulitDataSourceMap(multiSource));
        return multiSource;
    }


    public HashMap<Object, Object> loadMulitDataSourceMap(MultiDataSource multiSource) {
        HashMap<Object, Object> dataSourceMap = new HashMap<>();
        List<HikariDataSource> names = new ArrayList<>();
        if (tenant.getSingle()) {
            names = dbProperties.getNames();
        } else {
            names = this.getTenantData();
        }
        if (CollectionUtils.isEmpty(names)) {
            if (multiSource != null) {
                throw new RuntimeException(" please configure the data source! ");
            } else {
                return dataSourceMap;
            }
        }
        if (names.size() == 0) {
            if (multiSource != null) {
                throw new RuntimeException(" please configure the data source! ");
            } else {
                return dataSourceMap;
            }
        }
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        String path = "application.yml";
        factoryBean.setResources(new ClassPathResource(path));
        Properties properties = factoryBean.getObject();
        boolean seata_enabled = true;
        if (properties.get("seata.enabled") == null) {
            seata_enabled = false;
        } else {
            seata_enabled = Boolean.parseBoolean(properties.get("seata.enabled").toString());
        }

        if (multiSource != null) {
            if (seata_enabled) {
                multiSource.setDefaultTargetDataSource(new DataSourceProxy(names.get(0)));
            } else {
                multiSource.setDefaultTargetDataSource(names.get(0));
            }
        }
        int i = 0;
        for (HikariDataSource name : names) {
            name.setIdleTimeout(hikariConfig.getIdleTimeout());
//            name.setAutoCommit(false);
            name.setMaximumPoolSize(hikariConfig.getMaximumPoolSize());
            name.setMinimumIdle(hikariConfig.getMinimumIdle());
            name.setMaxLifetime(hikariConfig.getMaxLifetime());
            name.setConnectionTimeout(hikariConfig.getConnectionTimeout());
            name.setLeakDetectionThreshold(hikariConfig.getLeakDetectionThreshold());
            if (seata_enabled) {
                dataSourceMap.put(name.getPoolName(), new DataSourceProxy(name));
            } else {
                dataSourceMap.put(name.getPoolName(), name);
            }

        }
        return dataSourceMap;
    }

    public List<HikariDataSource> getTenantData() {
        List<HikariDataSource> hikariDataSources = new ArrayList<>();
        String version = getReleaseVersion();
        if (release_version.equals(version)) {
            return hikariDataSources;
        }
        release_version = version;
        log.info("准备获取平台租户数据");
        log.info("请求地址：" + this.tenant.getTenanturl() + "/tenantDataBase/getTenantData");
        TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl() + "/tenantDataBase/getTenantData", true);
        try {
            tenantHttp.addParameter("SYSTEM_TYPE", tenant.getSystemType());
            log.info("发送请求");
            tenantHttp.post();
            log.info("请求结果：" + tenantHttp.getContent());
            JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
                return hikariDataSources;
            }
            JSONArray data = JSONArray.parseArray(returnData.getString("data"));
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                HikariDataSource hikariDataSource = new HikariDataSource();
                hikariDataSource.setJdbcUrl(item.getString("CONNECT_STRING"));
                hikariDataSource.setDriverClassName(item.getString("DRIVER_CLASS_NAME"));
                hikariDataSource.setUsername(item.getString("CONNECT_USERID"));
                hikariDataSource.setPassword(item.getString("CONNECT_PWD"));
                hikariDataSource.setPoolName(item.getString("DATABASE_ID") + "-" + item.getString("TENANT_ID"));
                hikariDataSources.add(hikariDataSource);
            }
        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }
        return hikariDataSources;
    }

    public String getReleaseVersion() {

        String version = "null";
        log.info("准备获取平台租户发布版本");
        log.info("请求地址：" + this.tenant.getTenanturl() + "/tenant/getReleaseVersion");
        TenantHttp tenantHttp = new TenantHttp(this.tenant.getTenanturl() + "/tenant/getReleaseVersion", true);
        try {
            tenantHttp.addParameter("SYSTEM_TYPE", tenant.getSystemType());
            log.info("发送请求");
            tenantHttp.post();
            log.info("请求结果：" + tenantHttp.getContent());
            JSONObject returnData = JSONObject.parseObject(tenantHttp.getContent());
            if (!returnData.getBoolean("res")) {
                log.error("返回结果失败：" + returnData.getString("data"));
            }
            version = returnData.getJSONObject("data").getString("RELEASE_VERSION");
        } catch (Exception ex) {
            log.error("请求异常：" + ex.toString());
        }

        return version;
    }
}
