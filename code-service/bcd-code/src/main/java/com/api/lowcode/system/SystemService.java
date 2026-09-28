package com.api.lowcode.system;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

@Service
public class SystemService {

  @Resource
  private SqlEngineJson helper;

  private static final String TABLE = "lowcode_system";

  private static final String[][] MAPPING = {
      { "system_id", "id" }, { "system_name", "name" }, { "sort_order", "sort" }
  };

  public JSONArray listSystems() throws Exception {
    String[] cols = { "system_id", "system_name", "sort_order" };
    JSONArray rows = helper.queryAndMap(TABLE, cols, null, "sort_order", false, MAPPING);
    if (rows.isEmpty()) {
      String now = Instant.now().toString();
      JSONObject data = new JSONObject();
      data.put("system_id", "sys_default");
      data.put("system_name", "默认系统");
      data.put("sort_order", 0);
      data.put("created_at", now);
      SqlEngineJson.check(helper.insert(TABLE, data));
      rows = helper.queryAndMap(TABLE, cols, null, "sort_order", false, MAPPING);
    }
    return rows;
  }

  public void saveSystem(JSONObject system) throws Exception {
    String id = system.getString("id");
    String now = Instant.now().toString();
    JSONObject data = new JSONObject();
    data.put("system_id", id);
    data.put("system_name", system.getString("name"));
    data.put("sort_order", system.getInteger("sort"));
    data.put("created_at", now);
    SqlEngineJson.check(helper.upsert(TABLE, data, helper.eq("system_id", id)));
  }

  public void deleteSystem(String systemId) throws Exception {
    if ("sys_default".equals(systemId)) {
      return;
    }
    SqlEngineJson.check(helper.delete(TABLE, helper.eq("system_id", systemId)));
  }
}