package com.api.lowcode.perm;

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
public class PermissionService {

    @Resource
    private SqlEngineJson helper;

    public JSONArray listRoles() throws Exception {
        String[] cols = { "id", "code", "name", "enabled" };
        return helper.query("lc_role", cols, null, "code", false);
    }

    public JSONArray listGatewayPermissions(JSONObject params) throws Exception {
        String bizCode = params.getString("bizCode");
        String[] cols = { "id", "biz_code", "action", "role_code", "allowed" };
        if (bizCode != null && !bizCode.isEmpty()) {
            return helper.query("lc_permission_gateway", cols, helper.eq("biz_code", bizCode), "action", false);
        }
        return helper.query("lc_permission_gateway", cols, null, "biz_code", false);
    }

    public void saveGatewayPermission(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        if (data.getString("created_at") == null) {
            data.put("created_at", Instant.now().toString());
        }
        JSONArray conditions = mergeConditions(
                helper.eq("biz_code", data.getString("bizCode")),
                helper.eq("action", data.getString("action")));
        conditions.addAll(helper.eq("role_code", data.getString("roleCode")).subList(1, 2));
        ResMsg r = helper.upsert("lc_permission_gateway", data, conditions);
        SqlEngineJson.check(r);
    }

    public void deleteGatewayPermission(String id) throws Exception {
        ResMsg r = helper.delete("lc_permission_gateway", helper.eq("id", id));
        SqlEngineJson.check(r);
    }

    public JSONArray listFieldPermissions(JSONObject params) throws Exception {
        String bizCode = params.getString("bizCode");
        String[] cols = { "id", "biz_code", "field_name", "role_code", "perm_type" };
        if (bizCode != null && !bizCode.isEmpty()) {
            return helper.query("lc_permission_field", cols, helper.eq("biz_code", bizCode), "field_name", false);
        }
        return helper.query("lc_permission_field", cols, null, "biz_code", false);
    }

    public void saveFieldPermission(JSONObject data) throws Exception {
        if (data.getString("id") == null) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        if (data.getString("created_at") == null) {
            data.put("created_at", Instant.now().toString());
        }
        JSONArray conditions = mergeConditions(
                helper.eq("biz_code", data.getString("bizCode")),
                helper.eq("field_name", data.getString("fieldName")));
        conditions.addAll(helper.eq("role_code", data.getString("roleCode")).subList(1, 2));
        ResMsg r = helper.upsert("lc_permission_field", data, conditions);
        SqlEngineJson.check(r);
    }

    public void deleteFieldPermission(String id) throws Exception {
        ResMsg r = helper.delete("lc_permission_field", helper.eq("id", id));
        SqlEngineJson.check(r);
    }

    /** 获取当前用户权限元数据（前端 authStore.permissions 加载用） */
    public JSONObject getPermissionMeta(String userId) throws Exception {
        JSONObject meta = new JSONObject();
        JSONObject fieldPermissions = new JSONObject();
        JSONObject buttonPermissions = new JSONObject();
        JSONObject actionPermissions = new JSONObject();
        JSONObject dataScopes = new JSONObject();

        JSONArray fieldPerms = helper.query("lc_permission_field",
                new String[]{ "biz_code", "field_name", "role_code", "perm_type" }, null, null, false);
        log.info("getPermissionMeta: fieldPerms rows={}", fieldPerms == null ? 0 : fieldPerms.size());
        if (fieldPerms != null) {
            for (int i = 0; i < fieldPerms.size(); i++) {
                JSONObject p = fieldPerms.getJSONObject(i);
                String bizCode = p.getString("BIZ_CODE");
                if (bizCode == null) bizCode = p.getString("biz_code");
                if (!fieldPermissions.containsKey(bizCode)) {
                    fieldPermissions.put(bizCode, new JSONObject());
                }
                String fieldName = p.getString("FIELD_NAME");
                if (fieldName == null) fieldName = p.getString("field_name");
                String permType = p.getString("PERM_TYPE");
                if (permType == null) permType = p.getString("perm_type");
                ((JSONObject) fieldPermissions.get(bizCode)).put(fieldName, permType);
            }
        }

        JSONArray gwPerms = helper.query("lc_permission_gateway",
                new String[]{ "biz_code", "action", "role_code", "allowed" }, null, null, false);
        log.info("getPermissionMeta: gwPerms rows={}", gwPerms == null ? 0 : gwPerms.size());
        if (gwPerms != null) {
            for (int i = 0; i < gwPerms.size(); i++) {
                JSONObject p = gwPerms.getJSONObject(i);
                String bizCode = p.getString("BIZ_CODE");
                if (bizCode == null) bizCode = p.getString("biz_code");
                if (!actionPermissions.containsKey(bizCode)) {
                    actionPermissions.put(bizCode, new JSONObject());
                }
                String action = p.getString("ACTION");
                if (action == null) action = p.getString("action");
                Boolean allowed = p.getBoolean("ALLOWED");
                if (allowed == null) allowed = p.getBoolean("allowed");
                ((JSONObject) actionPermissions.get(bizCode)).put(action, allowed);
            }
        }

        meta.put("fieldPermissions", fieldPermissions);
        meta.put("buttonPermissions", buttonPermissions);
        meta.put("actionPermissions", actionPermissions);
        meta.put("dataScopes", dataScopes);
        log.info("getPermissionMeta: result keys={}, fieldPerms={}, actionPerms={}",
                meta.keySet(), fieldPermissions.size(), actionPermissions.size());
        return meta;
    }

    private JSONArray mergeConditions(JSONArray cond1, JSONArray cond2) {
        JSONArray merged = new JSONArray();
        merged.addAll(cond1);
        for (int i = 1; i < cond2.size(); i++) {
            merged.add(cond2.get(i));
        }
        return merged;
    }
}