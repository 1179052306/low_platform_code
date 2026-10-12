package com.api.lowcode.plugin.security;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class PluginPermissionService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private PluginSandbox sandbox;

    public void requestPermissions(String pluginId, java.util.List<String> permissions) throws Exception {
        String now = Instant.now().toString();
        for (String perm : permissions) {
            String id = UUID.randomUUID().toString().replace("-", "");
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("plugin_id", pluginId);
            data.put("permission", perm);
            data.put("status", "PENDING");
            data.put("created_at", now);
            try {
                ResMsg r = helper.upsert("lc_plugin_permission", data,
                        helper.eq("plugin_id", pluginId));
                SqlEngineJson.check(r);
            } catch (Exception e) {
                log.debug("[PluginPermission] 权限已存在跳过: {} {}", pluginId, perm);
            }
        }
        log.info("[PluginPermission] 权限请求提交: {} 共 {} 项", pluginId, permissions.size());
    }

    public void approve(String pluginId, String permission, String approvedBy) throws Exception {
        JSONObject data = new JSONObject();
        data.put("status", "APPROVED");
        data.put("approved_by", approvedBy);
        data.put("approved_at", Instant.now().toString());
        JSONArray conditions = helper.eq("plugin_id", pluginId);
        ResMsg r = helper.update("lc_plugin_permission", data, conditions);
        SqlEngineJson.check(r);
        sandbox.refresh(pluginId);
        log.info("[PluginPermission] 权限审批通过: {} {} by {}", pluginId, permission, approvedBy);
    }

    public void reject(String pluginId, String permission, String approvedBy) throws Exception {
        JSONObject data = new JSONObject();
        data.put("status", "REJECTED");
        data.put("approved_by", approvedBy);
        data.put("approved_at", Instant.now().toString());
        JSONArray conditions = helper.eq("plugin_id", pluginId);
        ResMsg r = helper.update("lc_plugin_permission", data, conditions);
        SqlEngineJson.check(r);
        sandbox.refresh(pluginId);
        log.info("[PluginPermission] 权限审批拒绝: {} {} by {}", pluginId, permission, approvedBy);
    }

    public JSONArray listPending() throws Exception {
        String[] cols = { "id", "plugin_id", "permission", "status", "created_at" };
        return helper.query("lc_plugin_permission", cols, helper.eq("status", "PENDING"), "created_at", true);
    }

    public JSONArray listByPlugin(String pluginId) throws Exception {
        String[] cols = { "id", "plugin_id", "permission", "status", "approved_by", "approved_at", "created_at" };
        return helper.query("lc_plugin_permission", cols, helper.eq("plugin_id", pluginId), "created_at", false);
    }
}