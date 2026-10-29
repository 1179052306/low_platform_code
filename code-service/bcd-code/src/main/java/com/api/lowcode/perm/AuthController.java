package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.CurrentUserContext;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/auth")
public class AuthController {

    @Resource
    private PermissionService permissionService;

    @GetMapping("/permissions")
    public ResMsg getPermissions(@RequestParam(required = false) String userId) {
        ResMsg r = new ResMsg();
        try {
            String uid = userId != null ? userId : CurrentUserContext.userId();
            r.setData(permissionService.getPermissionMeta(uid));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}