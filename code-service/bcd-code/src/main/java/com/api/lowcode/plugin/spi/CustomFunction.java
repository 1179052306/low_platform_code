package com.api.lowcode.plugin.spi;

import java.util.Map;

public interface CustomFunction extends Plugin {

    String functionName();

    Object invoke(Map<String, Object> args);
}