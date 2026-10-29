package com.api.system.init;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSource;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 系统管理页面配置化种子（Dogfooding）：5 个管理页由平台自身页面开发功能搭建（PageSchema 配置页面），
 * 菜单以 page:{pageId} 挂载，运行时渲染器渲染。全部幂等（存在即跳过，不覆盖设计器修改）。
 *
 * 种子内容：
 * 1. lowcode_page：5 个 PageSchema（db/system-pages/*.json 资源文件）
 * 2. lc_biz_meta / lc_field_meta / lc_action_meta：bizCode 壳元数据（sys_user 等指向 lc_* 真实表；
 *    写操作 EXTERNAL_API 桥接本服务 /api/sys/** 接口，ext_data_path=__RAW__ 响应透传）
 * 3. lowcode_api_registry：自定义按钮事件直调的 API 服务注册
 * 4. lc_menu：5 个菜单 component 由 vue:xxx 修正为 page:page_sys_xxx
 */
@Slf4j
@Component
@Order(2)
public class SystemPageSeeder implements CommandLineRunner {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private MultiDataSource multiDataSource;

    private static final String SYS_URL = "http://localhost:7011";

    @Override
    public void run(String... args) throws Exception {
        seedPages();
        seedMeta();
        seedApiRegistry();
        fixMenuComponents();
        log.info("bcd-system 页面配置化种子完成（5 个配置页面/元数据/API 注册/菜单挂载）");
    }

    // ==================== 1. 页面种子 ====================

    private void seedPages() throws Exception {
        String[] pages = {"sys_user", "sys_role", "sys_menu", "sys_online", "sys_oplog"};
        for (String name : pages) {
            String json = readResource("db/system-pages/" + name + ".json");
            JSONObject schema = JSONObject.parseObject(json);
            String pageId = schema.getString("pageId");
            if (pageExists(pageId)) {
                // 兼容升级：旧种子 schema_json 无 root 键（前端 importSchema 要求），补写
                ensureSchemaVersion(pageId, json);
                continue;
            }
            JSONObject data = new JSONObject();
            data.put("page_id", pageId);
            data.put("page_name", schema.getString("pageName"));
            data.put("table_name", schema.getString("tableName"));
            data.put("page_type", schema.getString("pageType"));
            data.put("schema_json", json);
            data.put("enabled", true);
            data.put("deleted", false);
            data.put("saved_at", now());
            data.put("updated_at", now());
            SqlEngineJson.check(helper.insert("lowcode_page", data));
            log.info("[PageSeed] 已插入配置页面：{}（{}）", pageId, schema.getString("pageName"));
        }
    }

    /** 种子版本升级：库内 schema_json 的 seedVersion 低于资源文件时覆盖整个 schema_json（含 root/children/width/height 等全部修正） */
    private void ensureSchemaVersion(String pageId, String json) {
        try {
            JSONArray rows = helper.query("lowcode_page", new String[]{"schema_json"},
                    helper.eq("page_id", pageId), null, false);
            if (rows.isEmpty()) {
                return;
            }
            String dbJson = String.valueOf(rows.getJSONObject(0).get("SCHEMA_JSON"));
            com.alibaba.fastjson2.JSONObject dbSchema = com.alibaba.fastjson2.JSONObject.parseObject(dbJson);
            int dbVer = dbSchema != null ? dbSchema.getIntValue("seedVersion", 0) : 0;
            if (dbVer < 7) {
                com.alibaba.fastjson2.JSONObject data = new com.alibaba.fastjson2.JSONObject();
                data.put("schema_json", json);
                data.put("updated_at", now());
                SqlEngineJson.check(helper.update("lowcode_page", data, helper.eq("page_id", pageId)));
                log.info("[PageSeed] 种子升级 v{}→v2：{}", dbVer, pageId);
            }
        } catch (Exception e) {
            log.warn("[PageSeed] 种子升级失败 {}: {}", pageId, e.getMessage());
        }
    }

    private boolean pageExists(String pageId) {
        try {
            JSONArray rows = helper.query("lowcode_page", new String[]{"page_id"},
                    helper.eq("page_id", pageId), null, false);
            return !rows.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void seedMeta() throws Exception {
        // ---- sys_user：query SINGLE 直查 lc_user；写操作 EXTERNAL_API 桥接本服务 ----
        seedBiz("sys_user", "用户管理", "lc_user", "id");
        seedField("sys_user", "username", "varchar", "string", true, true);
        seedField("sys_user", "real_name", "varchar", "string", false, false);
        seedField("sys_user", "email", "varchar", "string", false, false);
        seedField("sys_user", "mobile", "varchar", "string", false, false);
        seedField("sys_user", "status", "varchar", "string", false, false);
        seedField("sys_user", "last_login_at", "varchar", "string", false, false);
        seedAction("sys_user", "query", null, null, null, null);
        seedAction("sys_user", "create", "EXTERNAL_API", SYS_URL + "/api/sys/user/create", "POST", "__RAW__");
        seedAction("sys_user", "update", "EXTERNAL_API", SYS_URL + "/api/sys/user/update", "POST", "__RAW__");
        seedAction("sys_user", "delete", "EXTERNAL_API", SYS_URL + "/api/sys/user/delete", "POST", "__RAW__");

        // ---- sys_role：query EXTERNAL_API（含用户数/菜单数统计）；create/update→save；delete ----
        seedBiz("sys_role", "角色管理", "lc_role", "code");
        seedField("sys_role", "code", "varchar", "string", true, true);
        seedField("sys_role", "name", "varchar", "string", false, false);
        seedField("sys_role", "enabled", "boolean", "boolean", false, false);
        seedField("sys_role", "created_at", "varchar", "string", false, false);
        seedAction("sys_role", "query", "EXTERNAL_API", SYS_URL + "/api/sys/role/list", "GET", "data");
        seedAction("sys_role", "create", "EXTERNAL_API", SYS_URL + "/api/sys/role/save", "POST", "__RAW__");
        seedAction("sys_role", "update", "EXTERNAL_API", SYS_URL + "/api/sys/role/save", "POST", "__RAW__");
        seedAction("sys_role", "delete", "EXTERNAL_API", SYS_URL + "/api/sys/role/delete", "POST", "__RAW__");

        // ---- sys_menu：query SINGLE 直查 lc_menu；create/update→save；delete ----
        seedBiz("sys_menu", "菜单管理", "lc_menu", "id");
        seedField("sys_menu", "menu_name", "varchar", "string", true, false);
        seedField("sys_menu", "menu_code", "varchar", "string", true, true);
        seedField("sys_menu", "menu_type", "varchar", "string", false, false);
        seedField("sys_menu", "name_key", "varchar", "string", false, false);
        seedField("sys_menu", "parent_id", "varchar", "string", false, false);
        seedField("sys_menu", "route_path", "varchar", "string", false, false);
        seedField("sys_menu", "component", "varchar", "string", false, false);
        seedField("sys_menu", "permission_code", "varchar", "string", false, false);
        seedField("sys_menu", "icon", "varchar", "string", false, false);
        seedField("sys_menu", "sort_no", "int", "number", false, false);
        seedField("sys_menu", "visible", "boolean", "boolean", false, false);
        seedField("sys_menu", "status", "varchar", "string", false, false);
        seedAction("sys_menu", "query", null, null, null, null);
        seedAction("sys_menu", "create", "EXTERNAL_API", SYS_URL + "/api/sys/menu/save", "POST", "__RAW__");
        seedAction("sys_menu", "update", "EXTERNAL_API", SYS_URL + "/api/sys/menu/save", "POST", "__RAW__");
        seedAction("sys_menu", "delete", "EXTERNAL_API", SYS_URL + "/api/sys/menu/delete", "POST", "__RAW__");

        // ---- sys_online：query EXTERNAL_API（Redis 会话列表）；无写操作 ----
        seedBiz("sys_online", "在线会话", "lc_login_log", "jti");
        seedField("sys_online", "username", "varchar", "string", false, false);
        seedField("sys_online", "device_id", "varchar", "string", false, false);
        seedField("sys_online", "ip", "varchar", "string", false, false);
        seedField("sys_online", "login_at", "varchar", "string", false, false);
        seedField("sys_online", "ua", "varchar", "string", false, false);
        seedAction("sys_online", "query", "EXTERNAL_API", SYS_URL + "/api/sys/online/list", "GET", "data");

        // ---- sys_oplog：query SINGLE 直查 lc_op_log；只读 ----
        seedBiz("sys_oplog", "操作日志", "lc_op_log", "id");
        seedField("sys_oplog", "operated_at", "varchar", "string", false, false);
        seedField("sys_oplog", "user_id", "varchar", "string", false, false);
        seedField("sys_oplog", "username", "varchar", "string", false, false);
        seedField("sys_oplog", "module", "varchar", "string", false, false);
        seedField("sys_oplog", "action", "varchar", "string", false, false);
        seedField("sys_oplog", "params_digest", "varchar", "string", false, false);
        seedField("sys_oplog", "success", "boolean", "boolean", false, false);
        seedField("sys_oplog", "message", "varchar", "string", false, false);
        seedField("sys_oplog", "ip", "varchar", "string", false, false);
        seedAction("sys_oplog", "query", null, null, null, null);
    }

    private void seedBiz(String bizCode, String bizName, String mainTable, String pk) throws Exception {
        JSONArray rows = helper.query("lc_biz_meta", new String[]{"biz_code"},
                helper.eq("biz_code", bizCode), null, false);
        if (!rows.isEmpty()) {
            return;
        }
        JSONObject data = new JSONObject();
        data.put("id", uuid());
        data.put("biz_code", bizCode);
        data.put("biz_name", bizName);
        data.put("main_table", mainTable);
        data.put("primary_key_field", pk);
        data.put("key_strategy", "UUID");
        data.put("version", 1);
        data.put("created_at", now());
        data.put("updated_at", now());
        SqlEngineJson.check(helper.insert("lc_biz_meta", data));
        log.info("[MetaSeed] bizCode={}", bizCode);
    }

    private void seedField(String bizCode, String fieldName, String columnType, String dataType,
                           boolean required, boolean unique) throws Exception {
        JSONArray rows = helper.query("lc_field_meta", new String[]{"id"},
                com.api.system.common.Conds.merge(helper.eq("biz_code", bizCode),
                        helper.eq("field_name", fieldName)), null, false);
        if (!rows.isEmpty()) {
            return;
        }
        JSONObject data = new JSONObject();
        data.put("id", uuid());
        data.put("biz_code", bizCode);
        data.put("field_name", fieldName);
        data.put("column_type", columnType);
        data.put("data_type", dataType);
        data.put("is_required", required);
        data.put("is_unique", unique);
        data.put("is_system_field", false);
        data.put("sort_no", 0);
        data.put("created_at", now());
        SqlEngineJson.check(helper.insert("lc_field_meta", data));
    }

    private void seedAction(String bizCode, String action, String strategyType, String url,
                            String method, String dataPath) throws Exception {
        JSONArray rows = helper.query("lc_action_meta", new String[]{"id"},
                com.api.system.common.Conds.merge(helper.eq("biz_code", bizCode),
                        helper.eq("action", action)), null, false);
        if (!rows.isEmpty()) {
            return;
        }
        JSONObject data = new JSONObject();
        data.put("id", "action_" + bizCode + "_" + action);
        data.put("biz_code", bizCode);
        data.put("action", action);
        data.put("strategy_type", strategyType);
        data.put("enabled", true);
        data.put("ext_api_url", url);
        data.put("ext_api_method", method);
        data.put("ext_data_path", dataPath);
        data.put("created_at", now());
        SqlEngineJson.check(helper.insert("lc_action_meta", data));
    }

    // ==================== 3. API 服务注册种子 ====================

    private void seedApiRegistry() throws Exception {
        String[][] services = {
                {"sysUserResetPwd", "/api/sys/user/resetPassword", "POST", "system:user:resetPwd", "重置用户密码"},
                {"sysUserChangeStatus", "/api/sys/user/changeStatus", "POST", "system:user:status", "启用/禁用用户"},
                {"sysUserAssignRoles", "/api/sys/user/assignRoles", "POST", "system:user:assignRoles", "分配用户角色"},
                {"sysRoleAssignMenus", "/api/sys/role/assignMenus", "POST", "system:role:assignMenus", "角色分配菜单"},
                {"sysOnlineKick", "/api/sys/online/kick", "POST", "system:online:kick", "踢下线会话"},
        };
        for (String[] svc : services) {
            JSONArray rows = helper.query("lowcode_api_registry", new String[]{"id"},
                    helper.eq("id", svc[0]), null, false);
            if (!rows.isEmpty()) {
                continue;
            }
            JSONObject data = new JSONObject();
            data.put("id", svc[0]);
            data.put("url", svc[1]);
            data.put("method", svc[2]);
            data.put("perm_code", svc[3]);
            data.put("description", svc[4]);
            data.put("group_name", "system");
            data.put("updated_at", now());
            SqlEngineJson.check(helper.insert("lowcode_api_registry", data));
            log.info("[ApiSeed] {}", svc[0]);
        }
    }

    // ==================== 4. 菜单挂载修正 ====================

    private void fixMenuComponents() throws Exception {
        String[][] fixes = {
                {"system.user", "page:page_sys_user"},
                {"system.role", "page:page_sys_role"},
                {"system.menu", "page:page_sys_menu"},
                {"system.online", "page:page_sys_online"},
                {"system.oplog", "page:page_sys_oplog"},
        };
        for (String[] fix : fixes) {
            JSONObject data = new JSONObject();
            data.put("component", fix[1]);
            data.put("updated_at", now());
            SqlEngineJson.check(helper.update("lc_menu", data,
                    com.api.system.common.Conds.merge(helper.eq("menu_code", fix[0]),
                            helper.eq("menu_type", "menu"))));
        }
        log.info("[MenuSeed] 5 个系统管理菜单已切换为配置页面挂载（page:*）");
    }

    // ==================== 工具 ====================

    private String readResource(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("资源不存在: " + path);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}