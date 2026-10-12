package com.api.lowcode.plugin.spi;

public interface Plugin extends Lifecycle {

    String pluginId();

    String version();
}