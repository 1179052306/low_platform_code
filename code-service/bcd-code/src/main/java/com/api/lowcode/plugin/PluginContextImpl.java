package com.api.lowcode.plugin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.engine.CrudExecutor;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.MetaRegistry;
import com.api.lowcode.plugin.spi.ActionResult;
import com.api.lowcode.plugin.spi.PluginContext;
import com.api.lowcode.plugin.spi.PluginLogger;
import com.api.lowcode.tx.ActionContext;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PluginContextImpl implements PluginContext {

    private final String pluginId;
    private final SqlEngineJson helper;
    private final CrudExecutor crudExecutor;
    private final MetaRegistry metaRegistry;
    private final PluginLogger logger;

    public PluginContextImpl(String pluginId, SqlEngineJson helper, CrudExecutor crudExecutor,
            MetaRegistry metaRegistry) {
        this.pluginId = pluginId;
        this.helper = helper;
        this.crudExecutor = crudExecutor;
        this.metaRegistry = metaRegistry;
        this.logger = new PluginLoggerImpl(pluginId);
    }

    @Override
    public int insert(String bizCode, JSONObject data) throws Exception {
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            throw new IllegalArgumentException("未知 bizCode: " + bizCode);
        }
        ResMsg r = helper.insert(meta.getMainTable(), data);
        SqlEngineJson.check(r);
        return 1;
    }

    @Override
    public int update(String bizCode, JSONObject data, JSONObject conditions) throws Exception {
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            throw new IllegalArgumentException("未知 bizCode: " + bizCode);
        }
        JSONArray cond = helper.eq("id", conditions.getString("id"));
        ResMsg r = helper.update(meta.getMainTable(), data, cond);
        SqlEngineJson.check(r);
        return r.getData() != null ? Integer.parseInt(r.getData().toString()) : 0;
    }

    @Override
    public JSONObject queryById(String bizCode, Object id) throws Exception {
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            throw new IllegalArgumentException("未知 bizCode: " + bizCode);
        }
        JSONArray rows = helper.query(meta.getMainTable(), null, helper.eq("id", id), null, false);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.getJSONObject(0);
    }

    @Override
    public ActionResult invokeAction(String bizCode, String action, JSONObject payload) throws Exception {
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            return ActionResult.fail("UNKNOWN_BIZ", "未知 bizCode: " + bizCode);
        }
        ActionContext ctx = new ActionContext();
        ctx.setBizCode(bizCode);
        ctx.setAction(action);
        ctx.setPayload(payload);
        try {
            Object result = crudExecutor.execute(meta, action, ctx);
            return ActionResult.ok(result);
        } catch (Exception e) {
            return ActionResult.fail("ACTION_FAILED", e.getMessage());
        }
    }

    @Override
    public String getConfig(String key) {
        return System.getProperty("plugin." + pluginId + "." + key);
    }

    @Override
    public PluginLogger logger() {
        return logger;
    }

    @Override
    public void sendAsync(String topic, Object payload) {
        log.debug("[PluginContext] sendAsync 暂未实现(P3阶段)，topic={}", topic);
    }
}