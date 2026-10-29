package com.api.system.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/** 菜单管理（数据库操作全走 SqlEngineJson helper） */
@Service
public class MenuService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private RoleService roleService;

    private static final String[] MENU_COLS = {
            "id", "parent_id", "menu_type", "menu_name", "name_key", "menu_code",
            "route_path", "component", "permission_code", "icon", "sort_no", "visible", "status"
    };

    private static final String[][] MENU_MAPPING = {
            {"id", "id"}, {"parent_id", "parentId"}, {"menu_type", "menuType"}, {"menu_name", "menuName"},
            {"name_key", "nameKey"}, {"menu_code", "menuCode"}, {"route_path", "routePath"},
            {"component", "component"}, {"permission_code", "permissionCode"}, {"icon", "icon"},
            {"sort_no", "sortNo"}, {"visible", "visible"}, {"status", "status"}
    };

    private static final String[][] CODE_ONLY = {{"menu_code", "menuCode"}};
    private static final String[][] ID_ONLY = {{"id", "id"}};

    private JSONArray queryMenus(JSONArray conditions) throws Exception {
        return helper.queryAndMap("lc_menu", MENU_COLS, conditions, "sort_no", false, MENU_MAPPING);
    }

    /** 全量菜单树（管理端） */
    public JSONArray menuTree() throws Exception {
        return buildTree(queryMenus(null));
    }

    /** 当前用户菜单树（按角色过滤；子菜单授权而父目录未授权时补齐父目录链） */
    public JSONArray menuTreeForUser(List<String> roleCodes, boolean isAdmin) throws Exception {
        JSONArray granted;
        if (isAdmin) {
            granted = queryMenus(helper.eq("status", "enabled"));
        } else {
            if (roleCodes == null || roleCodes.isEmpty()) {
                return new JSONArray();
            }
            granted = queryMenus(com.api.system.common.Conds.merge(helper.in("role_code", roleCodes),
                    helper.eq("status", "enabled")));
            // DISTINCT 语义：JOIN 条件下 helper 走单表查询，改为按角色逐个查后合并去重
            granted = new JSONArray();
            for (String role : roleCodes) {
                JSONArray part = queryMenus(com.api.system.common.Conds.merge(
                        helper.in("role_code", new ArrayList<String>() {{
                            add(role);
                        }}), helper.eq("status", "enabled")));
                for (int i = 0; i < part.size(); i++) {
                    if (!containsId(granted, part.getJSONObject(i).getString("id"))) {
                        granted.add(part.getJSONObject(i));
                    }
                }
            }
        }
        if (!isAdmin) {
            granted = withParentChain(granted);
        }
        return buildTree(granted);
    }

    private JSONArray withParentChain(JSONArray granted) throws Exception {
        List<String> known = new ArrayList<>();
        for (int i = 0; i < granted.size(); i++) {
            known.add(granted.getJSONObject(i).getString("id"));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            List<JSONObject> append = new ArrayList<>();
            for (int i = 0; i < granted.size(); i++) {
                JSONObject node = granted.getJSONObject(i);
                String pid = node.getString("parentId");
                if (pid != null && !"0".equals(pid) && !known.contains(pid)) {
                    JSONArray parent = queryMenus(helper.eq("id", pid));
                    if (!parent.isEmpty()) {
                        JSONObject p = parent.getJSONObject(0);
                        if ("enabled".equals(p.getString("status")) && !containsId(granted, p.getString("id"))) {
                            append.add(p);
                        }
                        known.add(pid);
                        changed = true;
                    } else {
                        known.add(pid);
                    }
                }
            }
            for (JSONObject p : append) {
                granted.add(p);
            }
        }
        return granted;
    }

    private boolean containsId(JSONArray arr, String id) {
        for (int i = 0; i < arr.size(); i++) {
            if (id.equals(arr.getJSONObject(i).getString("id"))) {
                return true;
            }
        }
        return false;
    }

    private JSONArray buildTree(JSONArray rows) {
        JSONObject byId = new JSONObject(new java.util.LinkedHashMap<>());
        for (int i = 0; i < rows.size(); i++) {
            JSONObject r = rows.getJSONObject(i);
            r.put("children", new JSONArray());
            byId.put(r.getString("id"), r);
        }
        JSONArray roots = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject r = rows.getJSONObject(i);
            String pid = r.getString("parentId");
            JSONObject parent = (pid == null || "0".equals(pid)) ? null : byId.getJSONObject(pid);
            if (parent != null) {
                parent.getJSONArray("children").add(r);
            } else {
                roots.add(r);
            }
        }
        return roots;
    }

    public void saveMenu(JSONObject body) throws Exception {
        String id = body.getString("id");
        String menuType = com.api.system.common.BodyKeys.str(body, "menuType", "menu_type");
        if (menuType == null || !("dir".equals(menuType) || "menu".equals(menuType) || "button".equals(menuType))) {
            throw new IllegalArgumentException("菜单类型必须为 dir/menu/button");
        }
        String menuCode = com.api.system.common.BodyKeys.str(body, "menuCode", "menu_code");
        if (menuCode == null || menuCode.trim().isEmpty()) {
            throw new IllegalArgumentException("菜单编码不能为空");
        }
        JSONArray byCode = helper.queryAndMap("lc_menu", new String[]{"id"}, helper.eq("menu_code", menuCode), null, false, ID_ONLY);
        if (!byCode.isEmpty() && (id == null || !byCode.getJSONObject(0).getString("id").equals(id))) {
            throw new IllegalStateException("菜单编码已存在：" + menuCode);
        }
        JSONObject data = new JSONObject();
        data.put("menu_type", menuType);
        data.put("menu_name", com.api.system.common.BodyKeys.str(body, "menuName", "menu_name"));
        data.put("name_key", com.api.system.common.BodyKeys.str(body, "nameKey", "name_key"));
        data.put("menu_code", menuCode);
        data.put("route_path", com.api.system.common.BodyKeys.str(body, "routePath", "route_path"));
        data.put("component", com.api.system.common.BodyKeys.str(body, "component", "component"));
        data.put("permission_code", com.api.system.common.BodyKeys.str(body, "permissionCode", "permission_code"));
        data.put("icon", com.api.system.common.BodyKeys.str(body, "icon", "icon"));
        Integer sortNo = com.api.system.common.BodyKeys.integer(body, "sortNo", "sort_no");
        data.put("sort_no", sortNo == null ? 0 : sortNo);
        data.put("visible", com.api.system.common.BodyKeys.bool(body, "visible", "visible", true));
        data.put("updated_at", RoleService.now());
        if (id == null || id.isEmpty()) {
            data.put("id", RoleService.uuid());
            data.put("parent_id", com.api.system.common.BodyKeys.str(body, "parentId", "parent_id"));
            data.put("status", "enabled");
            data.put("created_at", RoleService.now());
            SqlEngineJson.check(helper.insert("lc_menu", data));
        } else {
            data.put("parent_id", com.api.system.common.BodyKeys.str(body, "parentId", "parent_id"));
            SqlEngineJson.check(helper.update("lc_menu", data, helper.eq("id", id)));
        }
    }

    /** 删除菜单：有子节点或被角色引用则拒绝 */
    public void deleteMenu(String menuId) throws Exception {
        JSONArray children = helper.queryAndMap("lc_menu", new String[]{"id"}, helper.eq("parent_id", menuId), null, false, ID_ONLY);
        if (!children.isEmpty()) {
            throw new IllegalStateException("存在子菜单，不允许删除");
        }
        JSONArray refs = helper.queryAndMap("lc_role_menu", new String[]{"role_code"},
                helper.eq("menu_id", menuId), null, false, CODE_ONLY.length > 0 ? new String[][]{{"role_code", "roleCode"}} : null);
        if (!refs.isEmpty()) {
            throw new IllegalStateException("菜单已被角色授权，请先取消授权");
        }
        SqlEngineJson.check(helper.delete("lc_menu", helper.eq("id", menuId)));
    }
}