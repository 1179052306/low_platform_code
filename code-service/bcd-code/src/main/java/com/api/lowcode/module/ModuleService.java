package com.api.lowcode.module;

import com.alibaba.fastjson2.JSONArray;
import com.server.sqlengine.SqlEngineJson;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
public class ModuleService {

  @Resource
  private SqlEngineJson helper;

  private static final String TABLE = "lowcode_module";

  private static final String[][] MAPPING = {
      { "module_id", "id" }, { "module_name", "name" }, { "sort_order", "sort" }, { "parent_id", "parentId" }
  };

  public JSONArray listModules() throws Exception {
    String[] cols = { "module_id", "module_name", "sort_order", "parent_id" };
    JSONArray rows = helper.queryAndMap(TABLE, cols, null, "sort_order", false, MAPPING);
    if (rows.isEmpty()) {
      String now = Instant.now().toString();
      JSONObject data = new JSONObject();
      data.put("module_id", "mod_default");
      data.put("module_name", "默认模块");
      data.put("sort_order", 0);
      data.put("created_at", now);
      SqlEngineJson.check(helper.insert(TABLE, data));
      rows = helper.queryAndMap(TABLE, cols, null, "sort_order", false, MAPPING);
    }
    return rows;
  }

  public void saveModule(JSONObject module) throws Exception {
    String id = module.getString("id");
    String now = Instant.now().toString();
    JSONObject data = new JSONObject();
    data.put("module_id", id);
    data.put("module_name", module.getString("name"));
    data.put("sort_order", module.getInteger("sort"));
    data.put("parent_id", module.getString("parentId"));
    data.put("created_at", now);
    SqlEngineJson.check(helper.upsert(TABLE, data, helper.eq("module_id", id)));
  }

  public void deleteModule(String moduleId) throws Exception {
    if ("mod_default".equals(moduleId)) {
      return;
    }
    Set<String> toDelete = new HashSet<>();
    collectChildren(moduleId, toDelete);
    String now = Instant.now().toString();
    for (String id : toDelete) {
      JSONObject data = new JSONObject();
      data.put("module_id", "mod_default");
      data.put("updated_at", now);
      SqlEngineJson.check(helper.update("lowcode_page", data, helper.eq("module_id", id)));
      SqlEngineJson.check(helper.delete(TABLE, helper.eq("module_id", id)));
    }
  }

  private void collectChildren(String id, Set<String> acc) throws Exception {
    acc.add(id);
    String[] cols = { "module_id" };
    JSONArray rows = helper.query(TABLE, cols, helper.eq("parent_id", id), null, false);
    for (int i = 0; i < rows.size(); i++) {
      String c = rows.getJSONObject(i).getString("MODULE_ID");
      collectChildren(c, acc);
    }
  }
}
