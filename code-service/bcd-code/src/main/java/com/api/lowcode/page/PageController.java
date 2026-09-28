package com.api.lowcode.page;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BusinessTableSyncService;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineFacade;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/lowcode/page")
public class PageController {

  @Resource
  private PageService pageService;

  @Resource
  private BusinessTableSyncService tableSyncService;

  @Resource
  private SqlEngineFacade sqlEngineFacade;

  @PostMapping("/all")
  public ResMsg all() {
    ResMsg r = new ResMsg();
    try {
      r.setData(pageService.allPages());
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/list")
  public ResMsg list(@RequestParam(defaultValue = "false") boolean deleted) {
    ResMsg r = new ResMsg();
    try {
      r.setData(pageService.listPages(deleted));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/{pageId}")
  public ResMsg get(@PathVariable String pageId) {
    ResMsg r = new ResMsg();
    try {
      JSONObject schema = pageService.getPage(pageId);
      if (schema == null) {
        r.setRes(false);
        r.setCode("404");
        r.setMsg("not found");
        return r;
      }
      r.setData(schema);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping
  public ResMsg save(@RequestBody JSONObject schema) {
    ResMsg r = new ResMsg();
    try {
      r.setData(pageService.savePage(schema));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @DeleteMapping("/{pageId}")
  public ResMsg delete(@PathVariable String pageId,
      @RequestParam(defaultValue = "false") boolean hard) {
    ResMsg r = new ResMsg();
    try {
      pageService.deletePage(pageId, hard);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PutMapping("/{pageId}/status")
  public ResMsg status(@PathVariable String pageId, @RequestBody JSONObject body) {
    ResMsg r = new ResMsg();
    try {
      pageService.setPageEnabled(pageId, body.getBooleanValue("enabled"));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PutMapping("/{pageId}/restore")
  public ResMsg restore(@PathVariable String pageId) {
    ResMsg r = new ResMsg();
    try {
      pageService.restorePage(pageId);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/orphan-fields")
  public ResMsg orphanFields(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String tableName = req.getString("tableName");
      JSONArray dbFieldsArr = req.getJSONArray("dbFields");
      List<String> dbFields = new ArrayList<>();
      if (dbFieldsArr != null) {
        for (int i = 0; i < dbFieldsArr.size(); i++) {
          dbFields.add(dbFieldsArr.getString(i));
        }
      }
      r.setData(tableSyncService.getOrphanFields(tableName, dbFields));
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/drop-field")
  public ResMsg dropField(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      tableSyncService.dropColumn(req.getString("tableName"), req.getString("columnName"));
      r.setMsg("字段删除成功");
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }

  @PostMapping("/query-data")
  public ResMsg queryData(@RequestBody JSONObject req) {
    ResMsg r = new ResMsg();
    try {
      String pageId = req.getString("pageId");
      if (pageId == null || pageId.isEmpty()) {
        r.setRes(false);
        r.setMsg("pageId 不能为空");
        return r;
      }
      JSONObject schema = pageService.getPage(pageId);
      if (schema == null) {
        r.setRes(false);
        r.setMsg("页面不存在: " + pageId);
        return r;
      }
      String tableName = schema.getString("tableName");
      if (tableName == null || tableName.isEmpty()) {
        r.setRes(false);
        r.setMsg("页面未配置数据表名");
        return r;
      }
      JSONObject queryReq = new JSONObject();
      queryReq.put("tableName", tableName);
      queryReq.put("alias", "T");
      Integer skip = req.getInteger("skip");
      Integer take = req.getInteger("take");
      if (skip != null && take != null) {
        int page = (skip / take) + 1;
        queryReq.put("page", page);
        queryReq.put("pageSize", take);
      } else if (take != null) {
        queryReq.put("pageSize", take);
      }
      String filterData = req.getString("filterData");
      if (filterData != null && !filterData.isEmpty()) {
        JSONArray conditions = JSONArray.parseArray(filterData);
        queryReq.put("conditions", conditions);
      }
      ResMsg queryResult = sqlEngineFacade.query(queryReq, null);
      if (!queryResult.getRes()) {
        r.setRes(false);
        r.setMsg(queryResult.getMsg());
        return r;
      }
      JSONArray data = (JSONArray) queryResult.getData();
      JSONObject result = new JSONObject();
      result.put("data", data);
      result.put("totalCount", data != null ? data.size() : 0);
      r.setData(result);
    } catch (Exception e) {
      r.setRes(false);
      r.setCode("500");
      r.setMsg(e.getMessage());
    }
    return r;
  }
}
