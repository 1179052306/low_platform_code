package com.security.oauth2.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class User implements UserDetails {

    private int id;
    private String userKey;
    private String username;
    private String password;
    private String jurisdiction;
    private String userNameDesc;
    private int supplierKey;
    private int userType;

    private String activationInfo;

    private String tenantId="";
    
    public String getUserNameDesc() {
        return userNameDesc;
    }

    public void setUserNameDesc(String userNameDesc) {
        this.userNameDesc = userNameDesc;
    }


    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }

    public int getSupplierKey() {
        return supplierKey;
    }

    public void setSupplierKey(int supplierKey) {
        this.supplierKey = supplierKey;
    }

    public String getActivationInfo() {
        return activationInfo;
    }

    public void setActivationInfo(String activationInfo) {
        this.activationInfo = activationInfo;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
