package com.api.auth.common;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONObject;

/** 多条件合并工具（helper.eq/in 返回 [connector, cond]，合并时去多余 connector） */
public final class Conds {

    private Conds() {
    }

    public static JSONArray merge(JSONArray... conditions) {
        JSONArray out = new JSONArray();
        JSONObject connector = new JSONObject();
        connector.put("Symbol", "and");
        out.add(connector);
        for (JSONArray c : conditions) {
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.size(); i++) {
                Object o = c.get(i);
                if (o instanceof JSONObject) {
                    JSONObject jo = (JSONObject) o;
                    if ("and".equals(jo.getString("Symbol")) && !jo.containsKey("Id")) {
                        continue;
                    }
                    out.add(jo);
                }
            }
        }
        return out;
    }
}
