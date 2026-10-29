package com.api.system.init;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSource;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 系统表初始化：db/sys_tables.sql（IF NOT EXISTS 幂等，DDL 走 JdbcTemplate——与 bcd-code
 * LowcodeTableInitializer 同模式，SqlEngineFacade 仅支持 DML）+ 基础菜单树 + ADMIN 全量授权（DML 全走 helper）。
 */
@Slf4j
@Component
@Order(1)
public class SystemTableInitializer implements CommandLineRunner {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private MultiDataSource multiDataSource;

    @Override
    public void run(String... args) throws Exception {
        executeDdl();
        initMenus();
        grantAdminAll();
        log.info("bcd-system 初始化完成（系统表/基础菜单树/ADMIN 全量授权）");
    }

    private void executeDdl() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/sys_tables.sql")) {
            if (is == null) {
                throw new IllegalStateException("db/sys_tables.sql 不存在");
            }
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            com.server.db.MultiDataSourceHolder.setDatasource("lowcode");
            try {
                JdbcTemplate jdbc = new JdbcTemplate(multiDataSource);
                for (String stmt : sql.split(";")) {
                    String s = stripComments(stmt);
                    if (s.isEmpty()) {
                        continue;
                    }
                    jdbc.execute(s);
                }
            } finally {
                com.server.db.MultiDataSourceHolder.clearDataSource();
            }
        }
    }

    /** 剥离 -- 注释行（语句块可能以注释开头，不能按整块 startsWith 判断跳过） */
    private static String stripComments(String stmt) {
        StringBuilder sb = new StringBuilder();
        for (String line : stmt.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private void initMenus() throws Exception {
        JSONArray existing = helper.query("lc_menu", new String[]{"id"}, null, null, false);
        if (!existing.isEmpty()) {
            return;
        }
        String now = java.time.Instant.now().toString();
        String sysDir = RoleIds.uuid();
        insertMenu(sysDir, null, "dir", "系统管理", "menu.system", "system", null, null, null, 10, now);
        String userMenu = RoleIds.uuid();
        insertMenu(userMenu, sysDir, "menu", "用户管理", "menu.system.user", "system.user", "/system/user", "vue:system/UserManageView", null, 1, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "新增用户", null, "system.user.create", null, null, "system:user:create", 1, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "修改用户", null, "system.user.update", null, null, "system:user:update", 2, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "启用禁用", null, "system.user.status", null, null, "system:user:status", 3, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "删除用户", null, "system.user.delete", null, null, "system:user:delete", 4, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "重置密码", null, "system.user.resetPwd", null, null, "system:user:resetPwd", 5, now);
        insertMenu(RoleIds.uuid(), userMenu, "button", "分配角色", null, "system.user.assignRoles", null, null, "system:user:assignRoles", 6, now);
        String roleMenu = RoleIds.uuid();
        insertMenu(roleMenu, sysDir, "menu", "角色管理", "menu.system.role", "system.role", "/system/role", "vue:system/RoleManageView", null, 2, now);
        insertMenu(RoleIds.uuid(), roleMenu, "button", "保存角色", null, "system.role.save", null, null, "system:role:save", 1, now);
        insertMenu(RoleIds.uuid(), roleMenu, "button", "删除角色", null, "system.role.delete", null, null, "system:role:delete", 2, now);
        insertMenu(RoleIds.uuid(), roleMenu, "button", "分配菜单", null, "system.role.assignMenus", null, null, "system:role:assignMenus", 3, now);
        String menuMenu = RoleIds.uuid();
        insertMenu(menuMenu, sysDir, "menu", "菜单管理", "menu.system.menu", "system.menu", "/system/menu", "vue:system/MenuManageView", null, 3, now);
        insertMenu(RoleIds.uuid(), menuMenu, "button", "保存菜单", null, "system.menu.save", null, null, "system:menu:save", 1, now);
        insertMenu(RoleIds.uuid(), menuMenu, "button", "删除菜单", null, "system.menu.delete", null, null, "system:menu:delete", 2, now);
        String onlineMenu = RoleIds.uuid();
        insertMenu(onlineMenu, sysDir, "menu", "在线会话", "menu.system.online", "system.online", "/system/online", "vue:system/OnlineManageView", null, 4, now);
        insertMenu(RoleIds.uuid(), onlineMenu, "button", "查看在线", null, "system.online.list", null, null, "system:online:list", 1, now);
        insertMenu(RoleIds.uuid(), onlineMenu, "button", "踢下线", null, "system.online.kick", null, null, "system:online:kick", 2, now);
        String oplogMenu = RoleIds.uuid();
        insertMenu(oplogMenu, sysDir, "menu", "操作日志", "menu.system.oplog", "system.oplog", "/system/oplog", "vue:system/OpLogView", null, 5, now);
    }

    private void insertMenu(String id, String parentId, String type, String name, String nameKey,
                            String code, String routePath, String component, String permCode, int sort, String now) throws Exception {
        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("parent_id", parentId);
        data.put("menu_type", type);
        data.put("menu_name", name);
        data.put("name_key", nameKey);
        data.put("menu_code", code);
        data.put("route_path", routePath);
        data.put("component", component);
        data.put("permission_code", permCode);
        data.put("sort_no", sort);
        data.put("visible", true);
        data.put("status", "enabled");
        data.put("created_at", now);
        data.put("updated_at", now);
        SqlEngineJson.check(helper.insert("lc_menu", data));
    }

    private void grantAdminAll() {
        try {
            JSONArray granted = helper.query("lc_role_menu", new String[]{"role_code"},
                    helper.eq("role_code", "ADMIN"), null, false);
            if (!granted.isEmpty()) {
                return;
            }
            JSONArray menus = helper.query("lc_menu", new String[]{"id"}, null, null, false);
            String now = java.time.Instant.now().toString();
            for (int i = 0; i < menus.size(); i++) {
                JSONObject data = new JSONObject();
                data.put("id", RoleIds.uuid());
                data.put("role_code", "ADMIN");
                data.put("menu_id", menus.getJSONObject(i).getString("ID"));
                data.put("created_at", now);
                SqlEngineJson.check(helper.insert("lc_role_menu", data));
            }
        } catch (Exception e) {
            log.warn("ADMIN 角色菜单授权处理失败：{}", e.getMessage());
        }
    }

    private static class RoleIds {
        static String uuid() {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }
}