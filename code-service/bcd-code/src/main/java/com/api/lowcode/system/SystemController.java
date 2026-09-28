package com.api.lowcode.system;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/system")
public class SystemController {

  @Resource
  private SystemService systemService;

  @PostMapping("/list")
  public ResMsg list() {
    ResMsg r = new ResMsg();
    try {
      r.setData(systemService.listSystems());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping
  public ResMsg save(@RequestBody JSONObject system) {
    ResMsg r = new ResMsg();
    try {
      systemService.saveSystem(system);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @DeleteMapping("/{systemId}")
  public ResMsg delete(@PathVariable String systemId) {
    ResMsg r = new ResMsg();
    try {
      systemService.deleteSystem(systemId);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}