package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/permission")
public class PermissionController {

    @Resource
    private PermissionService permissionService;

    @PostMapping("/gateway/list")
    public ResMsg listGatewayPermissions(@RequestBody(required = false) JSONObject params) {
        ResMsg r = new ResMsg();
        try {
            r.setData(permissionService.listGatewayPermissions(params != null ? params : new JSONObject()));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/gateway/save")
    public ResMsg saveGatewayPermission(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            permissionService.saveGatewayPermission(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/gateway/delete")
    public ResMsg deleteGatewayPermission(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            permissionService.deleteGatewayPermission(data.getString("id"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/field/list")
    public ResMsg listFieldPermissions(@RequestBody(required = false) JSONObject params) {
        ResMsg r = new ResMsg();
        try {
            r.setData(permissionService.listFieldPermissions(params != null ? params : new JSONObject()));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/field/save")
    public ResMsg saveFieldPermission(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            permissionService.saveFieldPermission(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/field/delete")
    public ResMsg deleteFieldPermission(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            permissionService.deleteFieldPermission(data.getString("id"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}