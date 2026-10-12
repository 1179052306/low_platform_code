package com.api.lowcode.tx;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class ActionContext {

    private String bizCode;
    private String action;
    private JSONObject payload;
    private JSONObject context;
    private Object result;

    public String getTenantId() {
        return context != null ? context.getString("tenantId") : null;
    }

    public String getUserId() {
        return context != null ? context.getString("userId") : null;
    }
}