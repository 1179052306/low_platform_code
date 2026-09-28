package com.api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.server", "com.api"})
public class BcdCodeApplication {


    public static void main(String[] args) {

        SpringApplication.run(BcdCodeApplication.class, args);
    }

}
