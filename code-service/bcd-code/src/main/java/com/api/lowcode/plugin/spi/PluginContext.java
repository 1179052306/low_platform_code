package com.api.lowcode.plugin.spi;

import com.alibaba.fastjson2.JSONObject;

public interface PluginContext {

    int insert(String bizCode, JSONObject data) throws Exception;

    int update(String bizCode, JSONObject data, JSONObject conditions) throws Exception;

    JSONObject queryById(String bizCode, Object id) throws Exception;

    ActionResult invokeAction(String bizCode, String action, JSONObject payload) throws Exception;

    String getConfig(String key);

    PluginLogger logger();

    void sendAsync(String topic, Object payload);
}