package com.api.lowcode.module;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/module")
public class ModuleController {

  @Resource
  private ModuleService moduleService;

  @PostMapping("/tree")
  public ResMsg tree() {
    ResMsg r = new ResMsg();
    try {
      r.setData(moduleService.listModules());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping
  public ResMsg save(@RequestBody JSONObject module) {
    ResMsg r = new ResMsg();
    try {
      moduleService.saveModule(module);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @DeleteMapping("/{moduleId}")
  public ResMsg delete(@PathVariable String moduleId) {
    ResMsg r = new ResMsg();
    try {
      moduleService.deleteModule(moduleId);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}
