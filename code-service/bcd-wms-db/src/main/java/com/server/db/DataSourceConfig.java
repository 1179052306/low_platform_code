package com.server.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DataSourceConfig {

    @Autowired
    LoadMultiDataSource loadMultiDataSource;
    @Autowired
    Tenant tenant;

    /**
     * 
     * 初始化数据源
     *
     * @Return MultiDataSource
     * @author liwei
     * @date: 2023/7/26
     **/
    @Bean(name = "multiDataSource")
    public MultiDataSource multiDataSource() throws Exception {

        return loadMultiDataSource.loadMultiDataSource();
    }

}