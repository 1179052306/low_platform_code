package com.api.system.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.system.security.RequirePerm;
import com.api.system.service.OpLogService;
import com.api.system.service.RoleService;
import com.api.system.service.SessionService;
import com.api.system.service.UserService;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/sys/role")
public class RoleController {

    @Resource
    private RoleService roleService;

    @Resource
    private UserService userService;

    @Resource
    private SessionService sessionService;

    @Resource
    private OpLogService opLogService;

    @GetMapping("/list")
    public ResMsg list() {
        ResMsg r = new ResMsg();
        try {
            r.setData(roleService.listRoles());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/save")
    @RequirePerm("system:role:save")
    public ResMsg save(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            roleService.saveRole(body);
            opLogService.record("role", "save", body.getString("code"), true, null, req);
            r.setData("ok");
        } catch (IllegalArgumentException | IllegalStateException e) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg(e.getMessage());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/delete")
    @RequirePerm("system:role:delete")
    public ResMsg delete(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            java.util.List<String> userIds = roleService.deleteRole(body.getString("code"));
            sessionService.kickRoleUsers(userIds);
            opLogService.record("role", "delete", body.getString("code"), true, null, req);
            r.setData("ok");
        } catch (IllegalStateException e) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg(e.getMessage());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/menus")
    public ResMsg menus(@RequestParam String roleCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(roleService.menuIdsOfRole(roleCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/assignMenus")
    @RequirePerm("system:role:assignMenus")
    public ResMsg assignMenus(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            JSONArray menuIds = body.getJSONArray("menuIds");
            roleService.assignMenus(body.getString("roleCode"),
                    menuIds == null ? java.util.Collections.emptyList() : menuIds.toJavaList(String.class));
            opLogService.record("role", "assignMenus",
                    body.getString("roleCode") + ":" + (menuIds == null ? 0 : menuIds.size()) + " 个菜单", true, null, req);
            r.setData("ok");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/users")
    public ResMsg users(@RequestParam String roleCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(userService.usersOfRole(roleCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}
