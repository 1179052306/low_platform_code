package com.api.lowcode.engine.event;

import lombok.Data;

import java.time.Instant;

@Data
public class BizEvent {

    private final String type;
    private final String bizCode;
    private final String action;
    private final Instant timestamp;
    private Object data;

    public BizEvent(String type, String bizCode, String action) {
        this.type = type;
        this.bizCode = bizCode;
        this.action = action;
        this.timestamp = Instant.now();
    }
}