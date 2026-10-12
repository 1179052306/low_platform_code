package com.api.lowcode.engine.event;

public interface EventBus {

    void fireSync(BizEvent event) throws Exception;

    void fireAsync(BizEvent event);
}