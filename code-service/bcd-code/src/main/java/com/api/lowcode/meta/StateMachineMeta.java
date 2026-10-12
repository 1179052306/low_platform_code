package com.api.lowcode.meta;

import lombok.Data;

@Data
public class StateMachineMeta {

    private String id;
    private String bizCode;
    private String currentState;
    private String nextState;
    private String event;
    private String guardExpr;
    private String action;
    private Boolean enabled;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}