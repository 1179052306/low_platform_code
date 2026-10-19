package com.api.lowcode.columnconfig;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/column-config")
public class ColumnConfigController {

  @Resource
  private ColumnConfigService columnConfigService;

  @GetMapping
  public ResMsg get(@RequestParam String userId, @RequestParam String pageId) {
    ResMsg r = new ResMsg();
    try {
      r.setData(columnConfigService.getColumnConfig(userId, pageId));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping
  public ResMsg save(@RequestBody JSONObject body) {
    ResMsg r = new ResMsg();
    try {
      String userId = body.getString("userId");
      String pageId = body.getString("pageId");
      JSONObject config = body.getJSONObject("config");
      columnConfigService.saveColumnConfig(userId, pageId, config);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}