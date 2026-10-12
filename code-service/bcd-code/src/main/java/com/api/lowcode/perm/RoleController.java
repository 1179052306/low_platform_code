package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/role")
public class RoleController {

    @Resource
    private PermissionService permissionService;

    @PostMapping("/list")
    public ResMsg listRoles() {
        ResMsg r = new ResMsg();
        try {
            r.setData(permissionService.listRoles());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}