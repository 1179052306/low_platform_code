package com.api.system.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.system.common.CurrentUserContext;
import com.server.sqlengine.SqlEngineJson;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/** 系统管理操作审计（lc_op_log，数据库操作全走 SqlEngineJson helper） */
@Service
public class OpLogService {

    @Resource
    private SqlEngineJson helper;

    private static final String[][] OPLOG_MAPPING = {
            {"id", "id"}, {"user_id", "userId"}, {"username", "username"}, {"module", "module"},
            {"action", "action"}, {"params_digest", "paramsDigest"}, {"success", "success"},
            {"message", "message"}, {"ip", "ip"}, {"operated_at", "operatedAt"}
    };

    public void record(String module, String action, Object paramsDigest, boolean success, String message,
                       HttpServletRequest req) {
        try {
            CurrentUserContext.Ctx ctx = CurrentUserContext.get();
            String digest = paramsDigest == null ? "" : String.valueOf(paramsDigest);
            if (digest.length() > 1000) {
                digest = digest.substring(0, 1000);
            }
            JSONObject data = new JSONObject();
            data.put("id", RoleService.uuid());
            data.put("user_id", ctx != null ? ctx.userId : null);
            data.put("username", ctx != null ? ctx.userName : null);
            data.put("module", module);
            data.put("action", action);
            data.put("params_digest", digest);
            data.put("success", success);
            data.put("message", message);
            data.put("ip", req == null ? "" : req.getRemoteAddr());
            data.put("operated_at", RoleService.now());
            SqlEngineJson.check(helper.insert("lc_op_log", data));
        } catch (Exception ignore) {
        }
    }

    public JSONArray list(int page, int size) throws Exception {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        JSONArray all = helper.queryAndMap("lc_op_log",
                new String[]{"id", "user_id", "username", "module", "action", "params_digest", "success", "message", "ip", "operated_at"},
                null, "operated_at", true, OPLOG_MAPPING);
        int total = all.size();
        int from = Math.min((p - 1) * s, total);
        int to = Math.min(from + s, total);
        return new JSONArray(all.subList(from, to));
    }
}