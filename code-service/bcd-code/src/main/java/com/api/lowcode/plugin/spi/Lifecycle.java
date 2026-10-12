package com.api.lowcode.plugin.spi;

public interface Lifecycle {

    void init(PluginContext ctx);

    void destroy();
}