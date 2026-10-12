package com.api.lowcode.engine.event;

public interface BizEventListener {

    boolean support(BizEvent event);

    void onEvent(BizEvent event) throws Exception;
}