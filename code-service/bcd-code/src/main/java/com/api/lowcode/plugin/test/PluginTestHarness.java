package com.api.lowcode.plugin.test;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.plugin.spi.ActionHook;
import com.api.lowcode.plugin.spi.HookPhase;
import com.api.lowcode.plugin.spi.Plugin;
import com.api.lowcode.tx.ActionContext;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PluginTestHarness {

    private final Connection connection;
    private final List<Plugin> plugins = new ArrayList<>();

    public PluginTestHarness(String jdbcUrl, String username, String password) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        log.info("[PluginTestHarness] 连接数据库: {}", jdbcUrl);
    }

    public void close() throws SQLException {
        for (Plugin plugin : plugins) {
            try {
                plugin.destroy();
            } catch (Exception e) {
                log.warn("[PluginTestHarness] 插件 destroy 异常: {}", plugin.pluginId(), e);
            }
        }
        connection.close();
    }

    public void registerPlugin(Plugin plugin) throws Exception {
        LocalPluginContext ctx = new LocalPluginContext(connection, plugin.pluginId());
        plugin.init(ctx);
        plugins.add(plugin);
        log.info("[PluginTestHarness] 注册插件: {} v{}", plugin.pluginId(), plugin.version());
    }

    public Object fireAction(String bizCode, String action, JSONObject payload) throws Exception {
        ActionContext ctx = new ActionContext();
        ctx.setBizCode(bizCode);
        ctx.setAction(action);
        ctx.setPayload(payload);
        log.info("[PluginTestHarness] fireAction bizCode={} action={}", bizCode, action);
        invokeHooks(bizCode, action, ctx, HookPhase.PRE);
        Object result = executeCrud(bizCode, action, payload);
        invokeHooks(bizCode, action, ctx, HookPhase.POST);
        log.info("[PluginTestHarness] fireAction 完成 bizCode={} action={} result={}", bizCode, action, result);
        return result;
    }

    public JSONObject queryById(String bizCode, Object id) throws Exception {
        String tableName = getTableName(bizCode);
        String sql = "SELECT * FROM \"" + tableName + "\" WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData meta = rs.getMetaData();
                JSONObject result = new JSONObject();
                for (int c = 1; c <= meta.getColumnCount(); c++) {
                    result.put(meta.getColumnName(c).toLowerCase(), rs.getObject(c));
                }
                return result;
            }
        }
    }

    private void invokeHooks(String bizCode, String action, ActionContext ctx, HookPhase phase) throws Exception {
        for (Plugin plugin : plugins) {
            if (plugin instanceof ActionHook) {
                ActionHook hook = (ActionHook) plugin;
                if (hook.phase() == phase && hook.support(bizCode, action)) {
                    log.debug("[PluginTestHarness] {} 钩子执行: {}", phase, hook.getClass().getName());
                    hook.invoke(ctx);
                }
            }
        }
    }

    private Object executeCrud(String bizCode, String action, JSONObject payload) throws Exception {
        String tableName = getTableName(bizCode);
        switch (action) {
            case "create":
                return executeInsert(tableName, payload);
            case "update":
                return executeUpdate(tableName, payload);
            case "delete":
                return executeDelete(tableName, payload);
            case "query":
                return executeQuery(tableName, payload);
            default:
                throw new IllegalArgumentException("未知 action: " + action);
        }
    }

    private JSONObject executeInsert(String tableName, JSONObject payload) throws Exception {
        JSONObject data = new JSONObject(payload);
        if (data.getString("id") == null) {
            data.put("id", java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        List<Object> params = new ArrayList<>(data.values());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        String sql = "INSERT INTO \"" + tableName + "\" (" + String.join(", ", data.keySet()) + ") VALUES ("
                + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
        JSONObject result = new JSONObject();
        result.put("id", data.getString("id"));
        return result;
    }

    private JSONObject executeUpdate(String tableName, JSONObject payload) throws Exception {
        String id = payload.getString("id");
        JSONObject data = new JSONObject(payload);
        data.remove("id");
        StringBuilder sql = new StringBuilder("UPDATE \"" + tableName + "\" SET ");
        List<Object> params = new ArrayList<>();
        int i = 0;
        for (String key : data.keySet()) {
            if (i > 0) sql.append(", ");
            sql.append(key).append(" = ?");
            params.add(data.get(key));
            i++;
        }
        sql.append(" WHERE id = ?");
        params.add(id);
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int j = 0; j < params.size(); j++) {
                ps.setObject(j + 1, params.get(j));
            }
            int rows = ps.executeUpdate();
            JSONObject result = new JSONObject();
            result.put("id", id);
            result.put("affectedRows", rows);
            return result;
        }
    }

    private JSONObject executeDelete(String tableName, JSONObject payload) throws Exception {
        String id = payload.getString("id");
        String sql = "DELETE FROM \"" + tableName + "\" WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            int rows = ps.executeUpdate();
            JSONObject result = new JSONObject();
            result.put("id", id);
            result.put("affectedRows", rows);
            return result;
        }
    }

    private JSONObject executeQuery(String tableName, JSONObject payload) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT * FROM \"" + tableName + "\" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        String logicDelete = payload.getString("logicDeleteField");
        if (logicDelete != null) {
            sql.append(" AND ").append(logicDelete).append(" = false");
        }
        Integer pageSize = payload.getInteger("pageSize");
        if (pageSize != null) {
            sql.append(" LIMIT ").append(pageSize);
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int j = 0; j < params.size(); j++) {
                ps.setObject(j + 1, params.get(j));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<JSONObject> rows = new ArrayList<>();
                while (rs.next()) {
                    JSONObject row = new JSONObject();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        row.put(meta.getColumnName(c).toLowerCase(), rs.getObject(c));
                    }
                    rows.add(row);
                }
                JSONObject result = new JSONObject();
                result.put("data", rows);
                result.put("totalCount", rows.size());
                return result;
            }
        }
    }

    private String getTableName(String bizCode) throws Exception {
        String sql = "SELECT main_table FROM lc_biz_meta WHERE biz_code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, bizCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        throw new IllegalArgumentException("未知 bizCode: " + bizCode);
    }
}