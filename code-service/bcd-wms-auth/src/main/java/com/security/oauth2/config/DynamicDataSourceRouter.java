package com.security.oauth2.config;

import com.server.db.DataSourceConfig;
import com.server.db.MultiDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DynamicDataSourceRouter {

    @Autowired
    private MultiDataSource dataSourceConfig; // 数据源配置类

    public DataSource getDataSource(String clientId) throws Exception {
        // 根据 clientId 决定使用哪个数据源
        try {
//            Map<Object, DataSource> dataSourceMap = dataSourceConfig.getResolvedDataSources();
//            Object dbName = dataSourceMap.keySet().stream().filter(item -> clientId.equals(item.toString().split("-")[1])).collect(Collectors.toList());
            return dataSourceConfig.getResolvedDataSources().get("project");
        } catch (Exception ex) {
            throw new Exception("client validate fail,no dataSource");
        }
    }
}

