package com.server.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 平台共享 UserDetails（bcd-wms-db 提供共享包）：
 * bcd-auth 授权服务器签发 token 时作为 principal 存入 RedisTokenStore（JDK 序列化），
 * bcd-system 资源服务器读取时反序列化——两服务共用此类保证字节兼容。
 */
public class LowcodeUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String username;
    private final String password;
    private final String realName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public LowcodeUserDetails(String userId, String username, String password, String realName, boolean enabled,
                              Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.enabled = enabled;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public String getUserId() {
        return userId;
    }

    public String getRealName() {
        return realName;
    }

    public List<String> getRoleCodes() {
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority a : authorities) {
            String v = a.getAuthority();
            if (v != null && v.startsWith("ROLE_")) {
                roles.add(v.substring(5));
            }
        }
        return roles;
    }

    public List<String> getPermCodes() {
        List<String> perms = new ArrayList<>();
        for (GrantedAuthority a : authorities) {
            String v = a.getAuthority();
            if (v != null && !v.startsWith("ROLE_") && !"*".equals(v)) {
                perms.add(v);
            }
        }
        return perms;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
        return enabled;
    }
}