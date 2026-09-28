package com.api.lowcode.registry;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

@Service
public class ApiRegistryService {

  @Resource
  private SqlEngineJson helper;

  private static final String TABLE = "lowcode_api_registry";

  private static final String[][] MAPPING = {
      { "id", "id" }, { "url", "url" }, { "method", "method" },
      { "perm_code", "permCode" }, { "description", "description" }, { "group_name", "group" }
  };

  public JSONArray listAll() throws Exception {
    String[] cols = { "id", "url", "method", "perm_code", "description", "group_name" };
    return helper.queryAndMap(TABLE, cols, null, "id", false, MAPPING);
  }

  public void saveAll(JSONArray list) throws Exception {
    String now = Instant.now().toString();
    for (int i = 0; i < list.size(); i++) {
      JSONObject s = list.getJSONObject(i);
      String id = s.getString("id");
      JSONObject data = new JSONObject();
      data.put("id", id);
      data.put("url", s.getString("url"));
      data.put("method", s.getString("method"));
      data.put("perm_code", s.getString("permCode"));
      data.put("description", s.getString("description"));
      data.put("group_name", s.getString("group"));
      data.put("updated_at", now);
      SqlEngineJson.check(helper.upsert(TABLE, data, helper.eq("id", id)));
    }
  }
}
