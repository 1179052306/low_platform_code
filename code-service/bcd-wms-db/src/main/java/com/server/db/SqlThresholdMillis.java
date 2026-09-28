package com.server.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author lw
 * @date: 2026/4/11
 * @description:
 **/
@Component
public class SqlThresholdMillis {
    @Value("${spring.sqlThresholdMillis:5000}")
    private long sqlThresholdMillis;

    public long getSqlThresholdMillis() {
        return sqlThresholdMillis;
    }
}
