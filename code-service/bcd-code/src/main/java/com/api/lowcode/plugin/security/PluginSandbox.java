package com.api.lowcode.plugin.security;

import com.alibaba.fastjson2.JSONArray;
import com.api.lowcode.common.BizException;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class PluginSandbox {

    @Resource
    private SqlEngineJson helper;

    private final ConcurrentMap<String, Set<String>> permissionCache = new ConcurrentHashMap<>();

    public boolean hasPermission(String pluginId, String permission) {
        Set<String> perms = permissionCache.get(pluginId);
        if (perms == null) {
            perms = loadPermissions(pluginId);
            permissionCache.put(pluginId, perms);
        }
        return perms.contains(permission) || perms.contains("*");
    }

    public void checkPermission(String pluginId, String permission) {
        if (!hasPermission(pluginId, permission)) {
            throw new BizException("PERMISSION_DENIED",
                    "插件 " + pluginId + " 缺少权限: " + permission);
        }
    }

    public void checkSqlPermission(String pluginId, String bizCode, String mode) {
        checkPermission(pluginId, "sql:" + bizCode + ":" + mode);
    }

    public void checkAsyncPermission(String pluginId, String topic) {
        checkPermission(pluginId, "async:topic:" + topic);
    }

    private Set<String> loadPermissions(String pluginId) {
        Set<String> perms = new HashSet<>();
        try {
            String[] cols = { "permission", "status" };
            JSONArray rows = helper.query("lc_plugin_permission", cols, helper.eq("plugin_id", pluginId), null,
                    false);
            if (rows == null) {
                return perms;
            }
            for (int i = 0; i < rows.size(); i++) {
                com.alibaba.fastjson2.JSONObject row = rows.getJSONObject(i);
                String status = row.getString("STATUS");
                if ("APPROVED".equals(status)) {
                    perms.add(row.getString("PERMISSION"));
                }
            }
        } catch (Exception e) {
            log.error("[PluginSandbox] 加载权限失败 pluginId={}", pluginId, e);
        }
        return perms;
    }

    public void refresh(String pluginId) {
        permissionCache.remove(pluginId);
    }

    public void refreshAll() {
        permissionCache.clear();
    }
}