package com.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lw
 * @date: 2025/2/28
 * @description:
 **/
public class TenantListPage {

    public  List<Map<String, Object>> listPage = new ArrayList<>();

    public String tenant;

    public List<Map<String, Object>> getListPage() {
        return listPage;
    }

    public void setListPage(List<Map<String, Object>> listPage) {
        this.listPage = listPage;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
