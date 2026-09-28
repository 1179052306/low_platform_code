package com.api.lowcode.scheme;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;

@Service
public class SearchSchemeService {

  @Resource
  private SqlEngineJson helper;

  private static final String TABLE = "lowcode_search_scheme";

  public JSONArray listByGridId(String gridId) throws Exception {
    String[] cols = { "scheme_name", "conditions", "is_default" };
    JSONArray rows = helper.query(TABLE, cols, helper.eq("grid_id", gridId), "id", false);
    JSONArray arr = new JSONArray();
    for (int i = 0; i < rows.size(); i++) {
      JSONObject row = rows.getJSONObject(i);
      JSONObject o = new JSONObject();
      o.put("name", row.getString("SCHEME_NAME"));
      o.put("isDefault", row.getBoolean("IS_DEFAULT"));
      String cond = row.getString("CONDITIONS");
      o.put("conditions", cond != null ? JSON.parse(cond) : new JSONArray());
      arr.add(o);
    }
    return arr;
  }

  public void saveAll(String gridId, JSONArray schemes) throws Exception {
    String now = Instant.now().toString();
    SqlEngineJson.check(helper.delete(TABLE, helper.eq("grid_id", gridId)));
    for (int i = 0; i < schemes.size(); i++) {
      JSONObject s = schemes.getJSONObject(i);
      Object conditions = s.get("conditions");
      String condJson = conditions != null ? JSON.toJSONString(conditions) : "[]";
      Boolean isDefault = s.getBoolean("isDefault");
      JSONObject data = new JSONObject();
      data.put("grid_id", gridId);
      data.put("scheme_name", s.getString("name"));
      data.put("conditions", condJson);
      data.put("is_default", isDefault != null && isDefault);
      data.put("created_at", now);
      SqlEngineJson.check(helper.insert(TABLE, data));
    }
  }
}
