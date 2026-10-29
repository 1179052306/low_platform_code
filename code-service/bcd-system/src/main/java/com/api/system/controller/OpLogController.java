package com.api.system.controller;

import com.api.system.service.OpLogService;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/sys/oplog")
public class OpLogController {

    @Resource
    private OpLogService opLogService;

    @GetMapping("/list")
    public ResMsg list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "15") int size) {
        ResMsg r = new ResMsg();
        try {
            r.setData(opLogService.list(page, size));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}
