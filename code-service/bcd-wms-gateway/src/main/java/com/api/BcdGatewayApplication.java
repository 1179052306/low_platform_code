package com.api;

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
//@EnableAsync(proxyTargetClass=true)
@EnableScheduling
@ComponentScan(basePackages = {"com.server","com.api"})
public class BcdGatewayApplication {
	@Autowired
	LogbackConfigurator logbackConfigurator;

	@PostConstruct
	public void init() throws Exception {

		logbackConfigurator.configureDbAppender();
	}
	public static void main(String[] args) {
		SpringApplication.run(BcdGatewayApplication.class, args);
	}

}
