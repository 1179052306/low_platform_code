package com.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lw
 * @date: 2025/2/28
 * @description:
 **/
public class TenantListJurisdictions {
    public TenantListJurisdictions( String tenant,List<Map<String, Object>> listJurisdictions) {
        this.listJurisdictions = listJurisdictions;
        this.tenant = tenant;
    }

    public  List<Map<String, Object>> listJurisdictions = new ArrayList<>();

    public String tenant;

    public List<Map<String, Object>> getListJurisdictions() {
        return listJurisdictions;
    }

    public void setListJurisdictions(List<Map<String, Object>> listJurisdictions) {
        this.listJurisdictions = listJurisdictions;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
