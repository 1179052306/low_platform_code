package com.api.lowcode.theme;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/lowcode/theme")
public class ThemeController {

  @Resource
  private ThemeService themeService;

  @PostMapping("/list")
  public ResMsg list(@RequestBody(required = false) JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String keyword = req != null ? req.getString("keyword") : null;
      r.setData(themeService.listThemes(keyword));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/save")
  public ResMsg save(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String id = themeService.saveTheme(req);
      JSONObject result = new JSONObject();
      result.put("id", id);
      r.setData(result);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/delete")
  public ResMsg delete(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      themeService.deleteTheme(req.getString("id"));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/detail")
  public ResMsg detail(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      JSONObject theme = themeService.getTheme(req.getString("id"));
      if (theme == null) {
        r.setRes(false);
        r.setCode("404");
        r.setMsg("主题不存在");
        return r;
      }
      r.setData(theme);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/default/get")
  public ResMsg getDefault() {
    ResMsg r = new ResMsg();
    try {
      String themeId = themeService.getDefaultThemeId();
      JSONObject result = new JSONObject();
      result.put("themeId", themeId);
      r.setData(result);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/default/set")
  public ResMsg setDefault(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      themeService.setDefaultThemeId(req.getString("themeId"));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/batch-apply")
  public ResMsg batchApply(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String themeId = req.getString("themeId");
      JSONArray pageIdsArr = req.getJSONArray("pageIds");
      List<String> pageIds = new ArrayList<String>();
      if (pageIdsArr != null) {
        for (int i = 0; i < pageIdsArr.size(); i++) {
          pageIds.add(pageIdsArr.getString(i));
        }
      }
      themeService.batchApply(themeId, pageIds);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}