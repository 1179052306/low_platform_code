package com.security;

import com.security.oauth2.config.ActivationConfig;
import com.server.log4.LogbackConfigurator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@ComponentScan(basePackages = {"com.server", "com.security", "com.security.oauth2.config", "com.security.common"})
public class BcdAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BcdAuthApplication.class, args);
    }

    @Autowired
    ActivationConfig activationConfig;
    @Autowired
    LogbackConfigurator logbackConfigurator;
    @PostConstruct
    public void init() throws Exception {
        logbackConfigurator.configureDbAppender();
        activationConfig.init();
    }


}
