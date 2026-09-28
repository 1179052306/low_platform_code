package com.security.model;

import com.alibaba.fastjson2.JSONArray;

/**
 * @author lw
 * @date: 2025/1/8
 * @description:
 **/
public class ActivationModel {
    String systemTpe;
    JSONArray data=new JSONArray();

    public String getSystemTpe() {
        return systemTpe;
    }

    public void setSystemTpe(String systemTpe) {
        this.systemTpe = systemTpe;
    }

    public JSONArray getData() {
        return data;
    }

    public void setData(JSONArray data) {
        this.data = data;
    }
}
