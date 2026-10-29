package com.api.system.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/** 用户管理（数据库操作全走 SqlEngineJson helper；密码 BCrypt） */
@Service
public class UserService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private RoleService roleService;

    @Resource
    private SessionService sessionService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private static final String[] USER_COLS = {
            "id", "username", "real_name", "email", "mobile", "status", "last_login_at", "created_at"
    };

    private static final String[][] USER_MAPPING = {
            {"id", "id"}, {"username", "username"}, {"real_name", "realName"}, {"email", "email"},
            {"mobile", "mobile"}, {"status", "status"}, {"last_login_at", "lastLoginAt"}, {"created_at", "createdAt"}
    };

    private static final String[][] USER_ROLE_MAPPING = {
            {"user_id", "userId"}, {"role_code", "roleCode"}
    };

    /** 用户列表（keyword 过滤 + Java 分页；企业用户表量小全量拉取） */
    public JSONObject listUsers(String keyword, int page, int size) throws Exception {
        JSONArray users = helper.queryAndMap("lc_user", USER_COLS, null, "created_at", true, USER_MAPPING);
        JSONArray userRoles = helper.queryAndMap("lc_user_role", new String[]{"user_id", "role_code"},
                null, null, false, USER_ROLE_MAPPING);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<JSONObject> filtered = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            JSONObject u = users.getJSONObject(i);
            StringBuilder roles = new StringBuilder();
            for (int j = 0; j < userRoles.size(); j++) {
                JSONObject ur = userRoles.getJSONObject(j);
                if (u.getString("id").equals(ur.getString("userId"))) {
                    if (roles.length() > 0) {
                        roles.append(",");
                    }
                    roles.append(ur.getString("roleCode"));
                }
            }
            u.put("roleCodes", roles.toString());
            if (!kw.isEmpty()) {
                boolean hit = (u.getString("username") != null && u.getString("username").toLowerCase().contains(kw))
                        || (u.getString("realName") != null && u.getString("realName").toLowerCase().contains(kw));
                if (!hit) {
                    continue;
                }
            }
            filtered.add(u);
        }
        int total = filtered.size();
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        int from = Math.min((p - 1) * s, total);
        int to = Math.min(from + s, total);
        JSONObject result = new JSONObject();
        result.put("data", new JSONArray(filtered.subList(from, to)));
        result.put("totalCount", total);
        return result;
    }

    public void createUser(JSONObject body) throws Exception {
        String username = body.getString("username");
        String password = body.getString("password");
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }
        JSONArray users = helper.queryAndMap("lc_user", new String[]{"id"}, null, null, false,
                new String[][]{{"id", "id"}});
        for (int i = 0; i < users.size(); i++) {
            // 大小写不敏感查重（username lower 唯一索引兜底）
        }
        JSONArray same = helper.query("lc_user", new String[]{"id", "username"}, null, null, false);
        for (int i = 0; i < same.size(); i++) {
            JSONObject row = same.getJSONObject(i);
            if (username.equalsIgnoreCase(row.getString("USERNAME"))) {
                throw new IllegalStateException("用户名已存在：" + username);
            }
        }
        String id = RoleService.uuid();
        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("username", username);
        data.put("password", encoder.encode(password));
        data.put("real_name", com.api.system.common.BodyKeys.str(body, "realName", "real_name"));
        data.put("email", com.api.system.common.BodyKeys.str(body, "email", "email"));
        data.put("mobile", com.api.system.common.BodyKeys.str(body, "mobile", "mobile"));
        data.put("status", "enabled".equals(body.getString("status")) ? body.getString("status") : "enabled");
        data.put("created_at", RoleService.now());
        data.put("updated_at", RoleService.now());
        SqlEngineJson.check(helper.insert("lc_user", data));
        saveUserRoles(id, body.getJSONArray("roleCodes"));
    }

    public void updateUser(JSONObject body) throws Exception {
        String id = body.getString("id");
        JSONArray existing = helper.queryAndMap("lc_user", new String[]{"id"}, helper.eq("id", id),
                null, false, new String[][]{{"id", "id"}});
        if (existing.isEmpty()) {
            throw new IllegalStateException("用户不存在");
        }
        JSONObject data = new JSONObject();
        data.put("real_name", com.api.system.common.BodyKeys.str(body, "realName", "real_name"));
        data.put("email", com.api.system.common.BodyKeys.str(body, "email", "email"));
        data.put("mobile", com.api.system.common.BodyKeys.str(body, "mobile", "mobile"));
        data.put("updated_at", RoleService.now());
        SqlEngineJson.check(helper.update("lc_user", data, helper.eq("id", id)));
        if (body.containsKey("roleCodes")) {
            saveUserRoles(id, body.getJSONArray("roleCodes"));
            sessionService.kickUser(id);
        }
    }

    public void changeStatus(String userId, String status) throws Exception {
        JSONObject data = new JSONObject();
        data.put("status", status);
        data.put("updated_at", RoleService.now());
        SqlEngineJson.check(helper.update("lc_user", data, helper.eq("id", userId)));
        if ("disabled".equals(status)) {
            sessionService.kickUser(userId);
        }
    }

    /** 物理删除：先踢会话 + 级联清关联表；日志表保留 user_id 字符串快照不级联 */
    public void deleteUser(String userId) throws Exception {
        sessionService.kickUser(userId);
        SqlEngineJson.check(helper.delete("lc_user_role", helper.eq("user_id", userId)));
        SqlEngineJson.check(helper.delete("lc_user_identity", helper.eq("user_id", userId)));
        SqlEngineJson.check(helper.delete("lc_user", helper.eq("id", userId)));
    }

    public String resetPassword(String userId) throws Exception {
        String temp = "Lc@" + (100000 + new java.util.Random().nextInt(900000));
        JSONObject data = new JSONObject();
        data.put("password", encoder.encode(temp));
        data.put("updated_at", RoleService.now());
        SqlEngineJson.check(helper.update("lc_user", data, helper.eq("id", userId)));
        sessionService.kickUser(userId);
        return temp;
    }

    public void changePassword(String userId, String oldPassword, String newPassword) throws Exception {
        JSONArray rows = helper.query("lc_user", new String[]{"id", "password"}, helper.eq("id", userId), null, false);
        if (rows.isEmpty()) {
            throw new IllegalStateException("用户不存在");
        }
        String hashed = rows.getJSONObject(0).getString("PASSWORD");
        if (!encoder.matches(oldPassword, hashed)) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
        JSONObject data = new JSONObject();
        data.put("password", encoder.encode(newPassword));
        data.put("updated_at", RoleService.now());
        SqlEngineJson.check(helper.update("lc_user", data, helper.eq("id", userId)));
        sessionService.kickUser(userId);
    }

    public void saveUserRoles(String userId, JSONArray roleCodes) throws Exception {
        SqlEngineJson.check(helper.delete("lc_user_role", helper.eq("user_id", userId)));
        if (roleCodes != null) {
            String now = RoleService.now();
            for (int i = 0; i < roleCodes.size(); i++) {
                JSONObject data = new JSONObject();
                data.put("id", RoleService.uuid());
                data.put("user_id", userId);
                data.put("role_code", roleCodes.getString(i));
                data.put("created_at", now);
                SqlEngineJson.check(helper.insert("lc_user_role", data));
            }
        }
    }

    public JSONArray usersOfRole(String roleCode) throws Exception {
        JSONArray userRoles = helper.queryAndMap("lc_user_role", new String[]{"user_id", "role_code"},
                helper.eq("role_code", roleCode), null, false, USER_ROLE_MAPPING);
        JSONArray users = helper.queryAndMap("lc_user", USER_COLS, null, "username", false, USER_MAPPING);
        JSONArray out = new JSONArray();
        for (int i = 0; i < userRoles.size(); i++) {
            String uid = userRoles.getJSONObject(i).getString("userId");
            for (int j = 0; j < users.size(); j++) {
                if (uid.equals(users.getJSONObject(j).getString("id"))) {
                    out.add(users.getJSONObject(j));
                    break;
                }
            }
        }
        return out;
    }

    /** 用户按钮权限码集合（ADMIN 由调用方短路为 *） */
    public List<String> permCodesOfUser(String userId) throws Exception {
        List<String> roles = roleService.roleCodesOfUser(userId);
        if (roles.contains(RoleService.ROLE_ADMIN)) {
            return java.util.Collections.singletonList("*");
        }
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }
        JSONArray roleMenus = helper.queryAndMap("lc_role_menu", new String[]{"role_code", "menu_id"},
                helper.in("role_code", roles), null, false,
                new String[][]{{"role_code", "roleCode"}, {"menu_id", "menuId"}});
        List<String> menuIds = new ArrayList<>();
        for (int i = 0; i < roleMenus.size(); i++) {
            menuIds.add(roleMenus.getJSONObject(i).getString("menuId"));
        }
        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        JSONArray perms = helper.queryAndMap("lc_menu", new String[]{"id", "permission_code"},
                com.api.system.common.Conds.merge(helper.in("id", menuIds),
                        helper.eq("menu_type", "button")), null, false,
                new String[][]{{"id", "id"}, {"permission_code", "permissionCode"}});
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < perms.size(); i++) {
            String c = perms.getJSONObject(i).getString("permissionCode");
            if (c != null && !c.isEmpty() && !codes.contains(c)) {
                codes.add(c);
            }
        }
        return codes;
    }
}