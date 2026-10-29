package com.api.system.controller;

import com.alibaba.fastjson2.JSONObject;
import com.api.system.security.RequirePerm;
import com.api.system.service.OpLogService;
import com.api.system.service.UserService;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/sys/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private OpLogService opLogService;

    @GetMapping("/list")
    public ResMsg list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "15") int size) {
        ResMsg r = new ResMsg();
        try {
            r.setData(userService.listUsers(keyword, page, size));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/create")
    @RequirePerm("system:user:create")
    public ResMsg create(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            userService.createUser(body);
            opLogService.record("user", "create", body.getString("username"), true, null, req);
        } catch (IllegalArgumentException | IllegalStateException e) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg(e.getMessage());
            opLogService.record("user", "create", body.getString("username"), false, e.getMessage(), req);
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/update")
    @RequirePerm("system:user:update")
    public ResMsg update(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            userService.updateUser(body);
            opLogService.record("user", "update", body.getString("id"), true, null, req);
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

    @PostMapping("/changeStatus")
    @RequirePerm("system:user:status")
    public ResMsg changeStatus(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            userService.changeStatus(body.getString("id"), body.getString("status"));
            opLogService.record("user", "changeStatus", body.getString("id") + ":" + body.getString("status"), true, null, req);
            r.setData("ok");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/delete")
    @RequirePerm("system:user:delete")
    public ResMsg delete(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            userService.deleteUser(body.getString("id"));
            opLogService.record("user", "delete", body.getString("id"), true, null, req);
            r.setData("ok");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/resetPassword")
    @RequirePerm("system:user:resetPwd")
    public ResMsg resetPassword(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            String temp = userService.resetPassword(body.getString("id"));
            opLogService.record("user", "resetPassword", body.getString("id"), true, null, req);
            r.setData(temp);
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/changePassword")
    public ResMsg changePassword(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        String uid = com.api.system.common.CurrentUserContext.userId();
        if (uid == null) {
            r.setRes(false);
            r.setCode("401");
            r.setMsg("未获取到用户上下文");
            return r;
        }
        try {
            userService.changePassword(uid, com.api.system.common.BodyKeys.str(body, "oldPassword", "old_password"), com.api.system.common.BodyKeys.str(body, "newPassword", "new_password"));
            opLogService.record("user", "changePassword", uid, true, null, req);
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

    @PostMapping("/assignRoles")
    @RequirePerm("system:user:assignRoles")
    public ResMsg assignRoles(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            userService.saveUserRoles(body.getString("id"), body.getJSONArray("roleCodes"));
            opLogService.record("user", "assignRoles", body.getString("id") + ":" + body.getJSONArray("roleCodes"), true, null, req);
            r.setData("ok");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}
