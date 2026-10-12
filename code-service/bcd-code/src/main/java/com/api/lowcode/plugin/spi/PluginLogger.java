package com.api.lowcode.plugin.spi;

public interface PluginLogger {

    void info(String format, Object... args);

    void warn(String format, Object... args);

    void error(String format, Object... args);

    void debug(String format, Object... args);
}