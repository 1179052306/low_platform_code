package com.api.system.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.system.common.CurrentUserContext;
import com.api.system.service.MenuService;
import com.api.system.service.RoleService;
import com.api.system.service.SessionService;
import com.api.system.service.UserService;
import com.api.system.security.RequirePerm;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/sys")
public class ProfileController {

    @Resource
    private RoleService roleService;

    @Resource
    private MenuService menuService;

    @Resource
    private UserService userService;

    @Resource
    private SessionService sessionService;

    @Resource
    private SqlEngineJson helper;

    private static final String[][] USER_BRIEF_MAPPING = {
            {"id", "id"}, {"username", "username"}, {"real_name", "realName"},
            {"email", "email"}, {"mobile", "mobile"}, {"status", "status"}
    };

    @GetMapping("/profile/me")
    public ResMsg me() {
        ResMsg r = new ResMsg();
        CurrentUserContext.Ctx ctx = CurrentUserContext.get();
        if (ctx == null) {
            r.setRes(false);
            r.setCode("401");
            r.setMsg("未获取到用户上下文");
            return r;
        }
        if (CurrentUserContext.DEBUG_ADMIN_ID.equals(ctx.userId)) {
            JSONObject u = new JSONObject();
            u.put("id", ctx.userId);
            u.put("username", ctx.userName);
            u.put("realName", "本地调试管理员");
            u.put("roles", new JSONArray(ctx.roles));
            u.put("permCodes", new JSONArray(Arrays.asList("*")));
            r.setData(u);
            return r;
        }
        try {
            JSONArray users = helper.queryAndMap("lc_user",
                    new String[]{"id", "username", "real_name", "email", "mobile", "status"},
                    helper.eq("username", ctx.userName), null, false, USER_BRIEF_MAPPING);
            if (!users.isEmpty()) {
                JSONObject u = users.getJSONObject(0);
                List<String> roles = roleService.roleCodesOfUser(u.getString("id"));
                u.put("roles", new JSONArray(roles));
                u.put("permCodes", new JSONArray(userService.permCodesOfUser(u.getString("id"))));
                r.setData(u);
            } else {
                r.setRes(false);
                r.setCode("404");
                r.setMsg("用户不存在");
            }
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/profile/menus")
    public ResMsg menus() {
        ResMsg r = new ResMsg();
        CurrentUserContext.Ctx ctx = CurrentUserContext.get();
        if (ctx == null) {
            r.setRes(false);
            r.setCode("401");
            r.setMsg("未获取到用户上下文");
            return r;
        }
        try {
            if (CurrentUserContext.DEBUG_ADMIN_ID.equals(ctx.userId)) {
                r.setData(menuService.menuTree());
                return r;
            }
            String uid = resolveUserId(ctx);
            List<String> roles = roleService.roleCodesOfUser(uid);
            r.setData(menuService.menuTreeForUser(roles, ctx.isAdmin()));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/profile/perms")
    public ResMsg perms() {
        ResMsg r = new ResMsg();
        CurrentUserContext.Ctx ctx = CurrentUserContext.get();
        if (ctx == null) {
            r.setRes(false);
            r.setCode("401");
            r.setMsg("未获取到用户上下文");
            return r;
        }
        if (CurrentUserContext.DEBUG_ADMIN_ID.equals(ctx.userId)) {
            r.setData(new JSONArray(Arrays.asList("*")));
            return r;
        }
        try {
            String uid = resolveUserId(ctx);
            r.setData(new JSONArray(userService.permCodesOfUser(uid)));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/online/list")
    @RequirePerm("system:online:list")
    public ResMsg onlineList() {
        ResMsg r = new ResMsg();
        try {
            r.setData(sessionService.onlineSessions());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/online/kick")
    @RequirePerm("system:online:kick")
    public ResMsg kick(@RequestBody JSONObject body) {
        ResMsg r = new ResMsg();
        try {
            String userId = body.getString("userId");
            String token = body.getString("token");
            int n;
            if (token != null && !token.isEmpty()) {
                sessionService.evictSession(token);
                n = 1;
            } else {
                n = sessionService.kickUser(userId);
            }
            r.setData(n);
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    /** principal 反序列化无 userId 时按 username 反查 */
    private String resolveUserId(CurrentUserContext.Ctx ctx) throws Exception {
        if (ctx.userId != null) {
            return ctx.userId;
        }
        JSONArray users = helper.queryAndMap("lc_user", new String[]{"id", "username"},
                helper.eq("username", ctx.userName), null, false,
                new String[][]{{"id", "id"}, {"username", "username"}});
        if (users.isEmpty()) {
            throw new IllegalStateException("用户不存在：" + ctx.userName);
        }
        return users.getJSONObject(0).getString("id");
    }
}