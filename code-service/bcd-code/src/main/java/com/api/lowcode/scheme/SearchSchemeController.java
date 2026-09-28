package com.api.lowcode.scheme;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/search-scheme")
public class SearchSchemeController {

  @Resource
  private SearchSchemeService searchSchemeService;

  @PostMapping("/list")
  public ResMsg list(@RequestParam String gridId) {
    ResMsg r = new ResMsg();
    try {
      r.setData(searchSchemeService.listByGridId(gridId));
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
      String gridId = body.getString("gridId");
      JSONArray schemes = body.getJSONArray("schemes");
      searchSchemeService.saveAll(gridId, schemes);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}
