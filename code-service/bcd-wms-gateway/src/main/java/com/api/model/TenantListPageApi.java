package com.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lw
 * @date: 2025/2/28
 * @description:
 **/
public class TenantListPageApi {

    public  List<Map<String, Object>> listPageApi = new ArrayList<>();

    public String tenant;

    public List<Map<String, Object>> getListPageApi() {
        return listPageApi;
    }

    public void setListPageApi(List<Map<String, Object>> listPageApi) {
        this.listPageApi = listPageApi;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
