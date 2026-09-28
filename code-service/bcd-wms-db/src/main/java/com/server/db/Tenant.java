package com.server.db;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * @author lw
 * @date: 2024/8/20
 * @description:
 **/
@Component
@Data
@Service
@ConfigurationProperties(prefix = "tenant")
public class Tenant {

    private Boolean single;
    private String tenanturl;
    private String systemType;
    private String defaultTenant;
    private String orgKey;
    public Boolean getSingle() {
        return single;
    }

    public void setSingle(Boolean single) {
        this.single = single;
    }

    public String getTenanturl() {
        return tenanturl;
    }

    public void setTenanturl(String tenanturl) {
        this.tenanturl = tenanturl;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }
}
