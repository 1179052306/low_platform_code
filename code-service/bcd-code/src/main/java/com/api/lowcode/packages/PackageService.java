package com.api.lowcode.packages;

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
public class PackageService {

    @Resource
    private SqlEngineJson helper;

    public JSONArray listPackages() throws Exception {
        String[] cols = { "id", "name", "version", "page_ids", "meta_keys", "description", "created_at", "updated_at" };
        return helper.query("lc_app_package", cols, null, "name", false);
    }

    public void savePackage(JSONObject data) throws Exception {
        if (data.getString("id") == null || data.getString("id").isEmpty()) {
            data.put("id", UUID.randomUUID().toString().replace("-", ""));
        }
        data.put("updated_at", Instant.now().toString());
        if (data.getString("created_at") == null) {
            data.put("created_at", Instant.now().toString());
        }
        ResMsg r = helper.upsert("lc_app_package", data, helper.eq("id", data.getString("id")));
        SqlEngineJson.check(r);
        log.info("[PackageService] 保存应用包: {}", data.getString("name"));
    }

    public void deletePackage(String id) throws Exception {
        ResMsg r = helper.delete("lc_app_package", helper.eq("id", id));
        SqlEngineJson.check(r);
        log.info("[PackageService] 删除应用包: {}", id);
    }
}