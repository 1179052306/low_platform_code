package com.api.lowcode.tx;

import com.server.db.DbConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Configuration
public class TxConfig {

    @Resource
    @Lazy
    private DbConfig dbConfig;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        HikariDataSource ds = dbConfig.getHikariDataSource("lowcode");
        return new DataSourceTransactionManager(ds);
    }
}