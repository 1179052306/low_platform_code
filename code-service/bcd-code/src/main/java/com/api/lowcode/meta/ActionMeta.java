package com.api.lowcode.meta;

import lombok.Data;

@Data
public class ActionMeta {

    private String id;
    private String bizCode;
    private String action;
    private String strategyType;
    private String preHookPlugins;
    private String postHookPlugins;
    private String statusTransition;
    private String dataScopeExpr;
    private Boolean enabled;
    // 外部 API 配置（strategyType=EXTERNAL_API 时用）
    private String extApiUrl;
    private String extApiMethod;
    private String extDataPath;
    private String extTotalPath;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}