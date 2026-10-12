package com.api.lowcode.dict;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class DictService {

    @Resource
    private SqlEngineJson helper;

    /** 字典主表列名映射：db 列名 → 前端 camelCase 字段名 */
    private static final String[][] DICT_MAPPING = {
            { "id", "id" },
            { "code", "code" },
            { "name", "name" },
            { "description", "description" },
            { "enabled", "enabled" },
            { "created_at", "createdAt" },
            { "updated_at", "updatedAt" }
    };

    /** 字典项表列名映射：db 列名 → 前端 camelCase 字段名 */
    private static final String[][] ITEM_MAPPING = {
            { "id", "id" },
            { "dict_code", "dictCode" },
            { "item_value", "value" },
            { "item_label", "label" },
            { "sort_no", "sortNo" },
            { "enabled", "enabled" },
            { "created_at", "createdAt" }
    };

    public JSONArray listDict() throws Exception {
        String[] cols = { "id", "code", "name", "description", "enabled", "created_at", "updated_at" };
        return helper.queryAndMap("lc_data_dict", cols, null, "code", false, DICT_MAPPING);
    }

    public void saveDict(JSONObject data) throws Exception {
        String code = data.getString("code");
        String id = data.getString("id");
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        String now = Instant.now().toString();
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("code", code);
        row.put("name", data.getString("name"));
        row.put("description", data.getString("description"));
        row.put("enabled", data.getBoolean("enabled"));
        row.put("updated_at", now);
        String createdAt = data.getString("createdAt");
        row.put("created_at", createdAt != null ? createdAt : now);
        ResMsg r = helper.upsert("lc_data_dict", row, helper.eq("code", code));
        SqlEngineJson.check(r);
        log.info("[DictService] 保存数据字典: {}", code);
    }

    public void deleteDict(String code) throws Exception {
        ResMsg r = helper.delete("lc_data_dict", helper.eq("code", code));
        SqlEngineJson.check(r);
        helper.delete("lc_data_dict_item", helper.eq("dict_code", code));
        log.info("[DictService] 删除数据字典: {}", code);
    }

    public JSONArray listDictItems(String dictCode) throws Exception {
        String[] cols = { "id", "dict_code", "item_value", "item_label", "sort_no", "enabled", "created_at" };
        return helper.queryAndMap("lc_data_dict_item", cols, helper.eq("dict_code", dictCode), "sort_no", true, ITEM_MAPPING);
    }

    public void saveDictItem(JSONObject data) throws Exception {
        String id = data.getString("id");
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("dict_code", data.getString("dictCode"));
        row.put("item_value", data.getString("value"));
        row.put("item_label", data.getString("label"));
        row.put("sort_no", data.getInteger("sortNo"));
        row.put("enabled", data.getBoolean("enabled"));
        String createdAt = data.getString("createdAt");
        row.put("created_at", createdAt != null ? createdAt : Instant.now().toString());
        ResMsg r = helper.upsert("lc_data_dict_item", row, helper.eq("id", id));
        SqlEngineJson.check(r);
    }

    public void deleteDictItem(String id) throws Exception {
        ResMsg r = helper.delete("lc_data_dict_item", helper.eq("id", id));
        SqlEngineJson.check(r);
    }
}