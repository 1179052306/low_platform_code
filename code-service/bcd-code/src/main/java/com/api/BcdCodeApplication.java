package com.api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * exclude SecurityAutoConfiguration：bcd-wms-db 传递引入 spring-security-web 后，
 * Spring Boot 默认安全链会对全部接口返回 401 + WWW-Authenticate: Basic，
 * 浏览器弹原生账号密码框；bcd-code 鉴权由 GatewaySecurityFilter（网关签名）承担，
 * 不使用 Spring Security 过滤器链。
 *
 * exclude SeataAutoConfiguration：bcd-wms-db 传递引入 seata-spring-boot-starter 1.3.0，
 * 其开关 key 为 spring.cloud.seata.enabled 且 matchIfMissing=true——一旦 application.yml
 * 未进 classpath（IDEA 增量构建偶发漏拷资源），配置缺失即默认开启，
 * GlobalTransactionScanner 因 applicationId=null 初始化失败导致启动崩溃。
 * 平台已决策不使用 Seata（零 @GlobalTransactional），代码级排除使启动不再依赖
 * yml 在场；yml 中 seata.enabled / spring.cloud.seata.enabled 双开关保留作第二道防线。
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        io.seata.spring.boot.autoconfigure.SeataAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {"com.server", "com.api"})
public class BcdCodeApplication {


    public static void main(String[] args) {

        SpringApplication.run(BcdCodeApplication.class, args);
    }

}
