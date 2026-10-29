package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.CurrentUserContext;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
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
        } catch (Throwable e) {
            log.error("getPermissions failed", e);
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
        return r;
    }
}