package com.api.model;

/**
 * @author lw
 * @date: 2025/1/6
 * @description:
 **/
public class RequestRes {

    public RequestRes() {

    }

    public RequestRes(String jurisdiction, String pageKey, String pageUrl) {
        this.jurisdiction = jurisdiction;
        this.pageUrl = pageUrl;
        this.pageKey = pageKey;
    }

    String systemType = "0";
    String jurisdiction = "";
    String pageUrl = "";
    String pageKey = "";
    String tenant = "";

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getPageKey() {
        return pageKey;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }


    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
