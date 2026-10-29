package com.api.auth.security;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.oauth.LowcodeUserDetails;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户加载（数据库操作全走 SqlEngineJson helper）：
 * lc_user（BCrypt）+ 角色（ROLE_ 前缀，进 JWT roles claim）
 * + 按钮权限码（无前缀 authority；ADMIN 角色短路不加载明细）。
 * 登录查询 lower(username) 大小写不敏感（全量拉取 Java 过滤，用户表量小）。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Resource
    private SqlEngineJson helper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            JSONArray users = helper.query("lc_user",
                    new String[]{"id", "username", "password", "real_name", "status"}, null, null, false);
            JSONObject target = null;
            String kw = username == null ? "" : username.toLowerCase();
            for (int i = 0; i < users.size(); i++) {
                JSONObject u = users.getJSONObject(i);
                if (kw.equals(u.getString("USERNAME") == null ? "" : u.getString("USERNAME").toLowerCase())) {
                    target = u;
                    break;
                }
            }
            if (target == null) {
                throw new UsernameNotFoundException("用户名或密码错误");
            }
            String uid = target.getString("ID");
            String uname = target.getString("USERNAME");
            String pwd = target.getString("PASSWORD");
            String realName = target.getString("REAL_NAME");
            boolean enabled = "enabled".equals(target.getString("STATUS"));

            List<GrantedAuthority> auths = new ArrayList<>();
            JSONArray userRoles = helper.queryAndMap("lc_user_role", new String[]{"user_id", "role_code"},
                    helper.eq("user_id", uid), null, false,
                    new String[][]{{"user_id", "userId"}, {"role_code", "roleCode"}});
            boolean admin = false;
            for (int i = 0; i < userRoles.size(); i++) {
                String role = userRoles.getJSONObject(i).getString("roleCode");
                auths.add(new SimpleGrantedAuthority("ROLE_" + role));
                if ("ADMIN".equals(role)) {
                    admin = true;
                }
            }
            if (!admin) {
                Set<String> permSet = new HashSet<>();
                List<String> roleList = new ArrayList<>();
                for (int i = 0; i < userRoles.size(); i++) {
                    roleList.add(userRoles.getJSONObject(i).getString("roleCode"));
                }
                if (!roleList.isEmpty()) {
                    JSONArray roleMenus = helper.queryAndMap("lc_role_menu",
                            new String[]{"role_code", "menu_id"}, helper.in("role_code", roleList), null, false,
                            new String[][]{{"role_code", "roleCode"}, {"menu_id", "menuId"}});
                    List<String> menuIds = new ArrayList<>();
                    for (int i = 0; i < roleMenus.size(); i++) {
                        menuIds.add(roleMenus.getJSONObject(i).getString("menuId"));
                    }
                    if (!menuIds.isEmpty()) {
                        JSONArray perms = helper.query("lc_menu", new String[]{"id", "permission_code"},
                                com.api.auth.common.Conds.merge(helper.in("id", menuIds),
                                        helper.eq("menu_type", "button")), null, false);
                        for (int i = 0; i < perms.size(); i++) {
                            String c = perms.getJSONObject(i).getString("PERMISSION_CODE");
                            if (c != null && !c.isEmpty()) {
                                permSet.add(c);
                            }
                        }
                    }
                }
                for (String c : permSet) {
                    auths.add(new SimpleGrantedAuthority(c));
                }
            } else {
                auths.add(new SimpleGrantedAuthority("*"));
            }
            return new LowcodeUserDetails(uid, uname, pwd, realName, enabled, auths);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("用户加载失败：" + e.getMessage());
        }
    }
}