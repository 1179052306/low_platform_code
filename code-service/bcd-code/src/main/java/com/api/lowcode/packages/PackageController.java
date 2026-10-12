package com.api.lowcode.packages;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/package")
public class PackageController {

    @Resource
    private PackageService packageService;

    @PostMapping("/list")
    public ResMsg listPackages() {
        ResMsg r = new ResMsg();
        try {
            r.setData(packageService.listPackages());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/save")
    public ResMsg savePackage(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            packageService.savePackage(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/delete")
    public ResMsg deletePackage(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            packageService.deletePackage(data.getString("id"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}