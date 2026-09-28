package com.api.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author lw
 * @date: 2026/4/11
 * @description:
 **/
@Component
public class GatewayThresholdMillis {
    @Value("${spring.gatewayThresholdMillis:3000}")
    private long gatewayThresholdMillis;

    public long getGatewayThresholdMillis() {
        return gatewayThresholdMillis;
    }
}
