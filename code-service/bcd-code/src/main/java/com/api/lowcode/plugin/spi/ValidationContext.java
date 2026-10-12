package com.api.lowcode.plugin.spi;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Map;

@Data
public class ValidationContext {

    private String bizCode;
    private String action;
    private JSONObject data;
    private Map<String, Object> variables;
    private String errorMessage;
    private boolean valid = true;

    public void fail(String message) {
        this.valid = false;
        this.errorMessage = message;
    }
}