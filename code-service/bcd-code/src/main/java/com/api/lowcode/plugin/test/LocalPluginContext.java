package com.api.lowcode.plugin.test;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.plugin.spi.ActionResult;
import com.api.lowcode.plugin.spi.PluginContext;
import com.api.lowcode.plugin.spi.PluginLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LocalPluginContext implements PluginContext {

    private final Connection connection;
    private final PluginLogger logger;

    public LocalPluginContext(Connection connection, String pluginId) {
        this.connection = connection;
        this.logger = new com.api.lowcode.plugin.PluginLoggerImpl(pluginId);
    }

    @Override
    public int insert(String bizCode, JSONObject data) throws Exception {
        String tableName = getTableName(bizCode);
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
            return ps.executeUpdate();
        }
    }

    @Override
    public int update(String bizCode, JSONObject data, JSONObject conditions) throws Exception {
        String tableName = getTableName(bizCode);
        StringBuilder sql = new StringBuilder("UPDATE \"" + tableName + "\" SET ");
        List<Object> params = new ArrayList<>();
        int i = 0;
        for (String key : data.keySet()) {
            if (i > 0) sql.append(", ");
            sql.append(key).append(" = ?");
            params.add(data.get(key));
            i++;
        }
        String idValue = conditions.getString("id");
        sql.append(" WHERE id = ?");
        params.add(idValue);
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int j = 0; j < params.size(); j++) {
                ps.setObject(j + 1, params.get(j));
            }
            return ps.executeUpdate();
        }
    }

    @Override
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

    @Override
    public ActionResult invokeAction(String bizCode, String action, JSONObject payload) throws Exception {
        logger.warn("LocalPluginContext.invokeAction 本地模拟不支持嵌套调用，请用 PluginTestHarness.fireAction");
        return ActionResult.fail("NOT_SUPPORTED", "本地模拟不支持 invokeAction，请用 PluginTestHarness.fireAction");
    }

    @Override
    public String getConfig(String key) {
        return System.getProperty(key);
    }

    @Override
    public PluginLogger logger() {
        return logger;
    }

    @Override
    public void sendAsync(String topic, Object payload) {
        logger.debug("LocalPluginContext.sendAsync 本地模拟忽略异步消息 topic={}", topic);
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