package com.api.lowcode.perm;

import com.alibaba.fastjson2.JSONArray;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/perm")
public class PagePermController {

  @Resource
  private PagePermService pagePermService;

  @PostMapping("/list")
  public ResMsg list(@RequestParam String pageId) {
    ResMsg r = new ResMsg();
    try {
      JSONArray perms = pagePermService.listByPage(pageId);
      r.setData(perms);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/by-code")
  public ResMsg byCode(@RequestParam String permCode) {
    ResMsg r = new ResMsg();
    try {
      JSONArray perms = pagePermService.listByPermCode(permCode);
      r.setData(perms);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}