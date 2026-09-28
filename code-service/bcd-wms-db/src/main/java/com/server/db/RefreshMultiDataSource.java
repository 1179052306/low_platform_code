package com.server.db;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.tenant_http.TenantHttp;
import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lw
 * @date: 2025/1/8
 * @description:
 **/
@Slf4j
@Component
public class RefreshMultiDataSource {

    @Autowired
    Tenant tenant;

    @Autowired
    MultiDataSource multiDataSource;

    @Autowired
    LoadMultiDataSource loadMultiDataSource;

    @Scheduled(cron = "0 0/3 * * * ?")
    public void refreshConfig() throws Exception {

        try {
            if (!tenant.getSingle()) {

                this.multiDataSource.resetTargetDataSource(loadMultiDataSource.loadMulitDataSourceMap(null));
            }

        } catch (Exception ex) {

        }
    }
}
