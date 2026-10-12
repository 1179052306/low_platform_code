package com.api.lowcode.plugin.spi;

import com.api.lowcode.engine.event.StateChangeEvent;

public interface StateChangeListener extends Plugin {

    boolean support(String bizCode, String event);

    void onStateChange(StateChangeEvent evt) throws Exception;
}