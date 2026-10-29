package com.api.system.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 角色管理（数据库操作全走 SqlEngineJson helper） */
@Service
public class RoleService {

    public static final String ROLE_ADMIN = "ADMIN";

    @Resource
    protected SqlEngineJson helper;

    private static final String[][] ROLE_MAPPING = {
            {"code", "code"}, {"name", "name"}, {"enabled", "enabled"}, {"created_at", "createdAt"}
    };

    private static final String[][] CODE_ONLY = {{"code", "code"}};
    private static final String[][] USER_ROLE_MAPPING = {
            {"user_id", "userId"}, {"role_code", "roleCode"}
    };
    private static final String[][] ROLE_MENU_MAPPING = {
            {"role_code", "roleCode"}, {"menu_id", "menuId"}
    };

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String now() {
        return java.time.Instant.now().toString();
    }

    public List<String> roleCodesOfUser(String userId) throws Exception {
        String[] cols = {"user_id", "role_code"};
        JSONArray rows = helper.queryAndMap("lc_user_role", cols, helper.eq("user_id", userId), null, false, USER_ROLE_MAPPING);
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            roles.add(rows.getJSONObject(i).getString("roleCode"));
        }
        return roles;
    }

    /** 角色列表（附带用户数/菜单数统计：全量拉取 Java 分组，系统表量小） */
    public JSONArray listRoles() throws Exception {
        JSONArray roles = helper.queryAndMap("lc_role",
                new String[]{"code", "name", "enabled", "created_at"}, null, "code", false, ROLE_MAPPING);
        JSONArray userRoles = helper.queryAndMap("lc_user_role",
                new String[]{"user_id", "role_code"}, null, null, false, USER_ROLE_MAPPING);
        JSONArray roleMenus = helper.queryAndMap("lc_role_menu",
                new String[]{"role_code", "menu_id"}, null, null, false, ROLE_MENU_MAPPING);
        for (int i = 0; i < roles.size(); i++) {
            JSONObject r = roles.getJSONObject(i);
            String code = r.getString("code");
            int uc = 0;
            for (int j = 0; j < userRoles.size(); j++) {
                if (code.equals(userRoles.getJSONObject(j).getString("roleCode"))) {
                    uc++;
                }
            }
            int mc = 0;
            for (int j = 0; j < roleMenus.size(); j++) {
                if (code.equals(roleMenus.getJSONObject(j).getString("roleCode"))) {
                    mc++;
                }
            }
            r.put("userCount", uc);
            r.put("menuCount", mc);
        }
        return roles;
    }

    public void saveRole(JSONObject body) throws Exception {
        String code = body.getString("code");
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        JSONObject data = new JSONObject();
        data.put("code", code);
        data.put("name", body.getString("name"));
        data.put("enabled", body.getBooleanValue("enabled", true));
        JSONArray existing = helper.queryAndMap("lc_role", new String[]{"code"}, helper.eq("code", code), null, false, CODE_ONLY);
        if (existing.isEmpty()) {
            data.put("id", uuid());
            data.put("created_at", now());
            SqlEngineJson.check(helper.insert("lc_role", data));
        } else {
            SqlEngineJson.check(helper.update("lc_role", data, helper.eq("code", code)));
        }
    }

    /** 角色删除：拒删 ADMIN + 级联清 4 张引用表 + 踢该角色下用户（由 Controller 调 SessionService） */
    public List<String> deleteRole(String roleCode) throws Exception {
        if (ROLE_ADMIN.equals(roleCode)) {
            throw new IllegalStateException("内置管理员角色不允许删除");
        }
        String[] urCols = {"user_id", "role_code"};
        JSONArray userRoles = helper.queryAndMap("lc_user_role", urCols, helper.eq("role_code", roleCode), null, false, USER_ROLE_MAPPING);
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < userRoles.size(); i++) {
            userIds.add(userRoles.getJSONObject(i).getString("userId"));
        }
        SqlEngineJson.check(helper.delete("lc_user_role", helper.eq("role_code", roleCode)));
        SqlEngineJson.check(helper.delete("lc_role_menu", helper.eq("role_code", roleCode)));
        SqlEngineJson.check(helper.delete("lc_permission_gateway", helper.eq("role_code", roleCode)));
        SqlEngineJson.check(helper.delete("lc_permission_field", helper.eq("role_code", roleCode)));
        SqlEngineJson.check(helper.delete("lc_role", helper.eq("code", roleCode)));
        return userIds;
    }

    public List<String> menuIdsOfRole(String roleCode) throws Exception {
        JSONArray rows = helper.queryAndMap("lc_role_menu", new String[]{"role_code", "menu_id"},
                helper.eq("role_code", roleCode), null, false, ROLE_MENU_MAPPING);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ids.add(rows.getJSONObject(i).getString("menuId"));
        }
        return ids;
    }

    /** 角色分配菜单（全量覆盖）；按钮权限在登录时实时查库，重登录后生效 */
    public void assignMenus(String roleCode, List<String> menuIds) throws Exception {
        SqlEngineJson.check(helper.delete("lc_role_menu", helper.eq("role_code", roleCode)));
        if (menuIds != null) {
            String now = now();
            for (String menuId : menuIds) {
                JSONObject data = new JSONObject();
                data.put("id", uuid());
                data.put("role_code", roleCode);
                data.put("menu_id", menuId);
                data.put("created_at", now);
                SqlEngineJson.check(helper.insert("lc_role_menu", data));
            }
        }
    }
}