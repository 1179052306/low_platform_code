package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

/**
 * 权限同步服务 —— 页面保存时自动将字段/操作元数据同步到权限表，
 * 为每个启用角色的每个字段/操作创建默认权限记录。
 *
 * <p>
 * 保存页面时由 {@code PageService.savePage} 调用 {@link #syncPermissions}，
 * 从 lc_field_meta / lc_action_meta 读取已同步的元数据，
 * 结合 lc_role 中所有启用角色，批量 upsert 到 lc_permission_field / lc_permission_gateway。
 * </p>
 */
@Slf4j
@Service
public class PermSyncService {

    @Resource
    private SqlEngineJson helper;

    public void syncPermissions(JSONObject schema) {
        String pageType = schema.getString("pageType");
        if (!"document".equals(pageType) && !"basedata".equals(pageType)) {
            return;
        }

        String tableName = schema.getString("tableName");
        if (tableName == null || tableName.trim().isEmpty() || tableName.startsWith("sys_")) {
            return;
        }

        String bizCode = tableName.trim();
        String now = Instant.now().toString();

        try {
            JSONArray roles = helper.query("lc_role", new String[]{"code"}, helper.eq("enabled", true), null, false);
            if (roles.isEmpty()) return;

            JSONArray fields = helper.query("lc_field_meta", new String[]{"field_name"}, helper.eq("biz_code", bizCode), null, false);
            JSONArray actions = helper.query("lc_action_meta", new String[]{"action"}, helper.eq("biz_code", bizCode), null, false);

            for (int r = 0; r < roles.size(); r++) {
                String roleCode = roles.getJSONObject(r).getString("CODE");
                if (roleCode == null || roleCode.isEmpty()) continue;

                for (int f = 0; f < fields.size(); f++) {
                    String fieldName = fields.getJSONObject(f).getString("FIELD_NAME");
                    if (fieldName == null || fieldName.isEmpty()) continue;
                    upsertFieldPermission(bizCode, fieldName, roleCode, "visible", now);
                }

                for (int a = 0; a < actions.size(); a++) {
                    String action = actions.getJSONObject(a).getString("ACTION");
                    if (action == null || action.isEmpty()) continue;
                    upsertGatewayPermission(bizCode, action, roleCode, true, now);
                }
            }

            log.info("[PermSync] 同步权限成功: bizCode={}, roles={}, fields={}, actions={}", bizCode, roles.size(), fields.size(), actions.size());
        } catch (Exception e) {
            log.error("[PermSync] 同步权限失败: bizCode={}, {}", bizCode, e.getMessage(), e);
        }
    }

    private void upsertFieldPermission(String bizCode, String fieldName, String roleCode, String permType, String now) throws Exception {
        JSONArray conditions = mergeConditions(
            helper.eq("biz_code", bizCode),
            helper.eq("field_name", fieldName));
        conditions.addAll(helper.eq("role_code", roleCode).subList(1, 2));

        JSONObject data = new JSONObject();
        data.put("biz_code", bizCode);
        data.put("field_name", fieldName);
        data.put("role_code", roleCode);
        data.put("perm_type", permType);
        data.put("created_at", now);

        ResMsg r = helper.upsert("lc_permission_field", data, conditions);
        SqlEngineJson.check(r);
    }

    private void upsertGatewayPermission(String bizCode, String action, String roleCode, boolean allowed, String now) throws Exception {
        JSONArray conditions = mergeConditions(
            helper.eq("biz_code", bizCode),
            helper.eq("action", action));
        conditions.addAll(helper.eq("role_code", roleCode).subList(1, 2));

        JSONObject data = new JSONObject();
        data.put("biz_code", bizCode);
        data.put("action", action);
        data.put("role_code", roleCode);
        data.put("allowed", allowed);
        data.put("created_at", now);

        ResMsg r = helper.upsert("lc_permission_gateway", data, conditions);
        SqlEngineJson.check(r);
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