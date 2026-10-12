package com.api.lowcode.engine.event;

import lombok.Data;

@Data
public class EventSubscription {

    private String id;
    private String bizCode;
    private String eventType;
    private String handlerType;
    private String handlerConfig;
    private Boolean enabled;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}