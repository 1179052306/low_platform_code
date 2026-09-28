package com.api.lowcode.registry;

import com.alibaba.fastjson2.JSONArray;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/api-registry")
public class ApiRegistryController {

  @Resource
  private ApiRegistryService apiRegistryService;

  @PostMapping("/list")
  public ResMsg list() {
    ResMsg r = new ResMsg();
    try {
      r.setData(apiRegistryService.listAll());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping
  public ResMsg save(@RequestBody JSONArray list) {
    ResMsg r = new ResMsg();
    try {
      apiRegistryService.saveAll(list);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}
