package com.api.system.controller;

import com.alibaba.fastjson2.JSONObject;
import com.api.system.security.RequirePerm;
import com.api.system.service.MenuService;
import com.api.system.service.OpLogService;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/sys/menu")
public class MenuController {

    @Resource
    private MenuService menuService;

    @Resource
    private OpLogService opLogService;

    @GetMapping("/tree")
    public ResMsg tree() {
        ResMsg r = new ResMsg();
        try {
            r.setData(menuService.menuTree());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/save")
    @RequirePerm("system:menu:save")
    public ResMsg save(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            menuService.saveMenu(body);
            opLogService.record("menu", "save", body.getString("menuCode"), true, null, req);
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
    @RequirePerm("system:menu:delete")
    public ResMsg delete(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        try {
            menuService.deleteMenu(body.getString("id"));
            opLogService.record("menu", "delete", body.getString("id"), true, null, req);
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
}
