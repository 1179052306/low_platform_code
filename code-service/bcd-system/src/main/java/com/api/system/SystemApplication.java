package com.api.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 系统管理微服务（用户/角色/菜单/在线会话/操作日志），数据库操作全走 SqlEngineJson helper
 *
 * exclude SeataAutoConfiguration：bcd-wms-db 传递引入 seata-spring-boot-starter 1.3.0，
 * 其开关 key 为 spring.cloud.seata.enabled 且 matchIfMissing=true——一旦 application.yml
 * 未进 classpath（IDEA 增量构建偶发漏拷资源），配置缺失即默认开启，
 * GlobalTransactionScanner 因 applicationId=null 初始化失败导致启动崩溃。
 * 平台不使用 Seata（零 @GlobalTransactional），代码级排除使启动不依赖 yml 在场。
 */
@SpringBootApplication(exclude = {
        io.seata.spring.boot.autoconfigure.SeataAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.server", "com.api"})
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}