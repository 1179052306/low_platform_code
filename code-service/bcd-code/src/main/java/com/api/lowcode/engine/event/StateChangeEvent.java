package com.api.lowcode.engine.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StateChangeEvent extends BizEvent {

    private String businessId;
    private String fromState;
    private String toState;
    private String event;

    public StateChangeEvent(String bizCode, String action, String businessId,
            String fromState, String toState, String event) {
        super("STATE_CHANGE", bizCode, action);
        this.businessId = businessId;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
    }
}