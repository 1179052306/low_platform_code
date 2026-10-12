package com.api.lowcode.plugin.spi;

import com.api.lowcode.tx.ActionContext;

public interface ActionHook extends Plugin {

    HookPhase phase();

    boolean support(String bizCode, String action);

    void invoke(ActionContext ctx) throws Exception;
}