package com.api.system.security;

import com.api.system.common.CurrentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资源服务器：/api/sys/** 走 RedisTokenStore 校验（与 bcd-auth 共享同一 Redis 与 prefix，
 * 登出/踢人删 token 即时失效）。
 * local-debug=true 时放行并按内置 admin 填充上下文（生产必须 false）。
 */
@Configuration
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    @Value("${auth.security.local-debug:true}")
    private boolean localDebug;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public TokenStore tokenStore() {
        RedisTokenStore store = new RedisTokenStore(redisConnectionFactory);
        store.setPrefix("auth:");
        // 与 bcd-auth 保持一致：共享 DeviceAuthenticationKeyGenerator（认证索引 key 纳入 deviceId，多端并存）
        store.setAuthenticationKeyGenerator(new com.server.oauth.DeviceAuthenticationKeyGenerator());
        // 定向放行白名单版 JDK 序列化（默认策略拒绝平台自定义 principal 类反序列化）
        store.setSerializationStrategy(new com.server.oauth.PlatformTokenSerializationStrategy());
        return store;
    }

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId("lowcode").tokenStore(tokenStore());
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        if (localDebug) {
            org.slf4j.LoggerFactory.getLogger(ResourceServerConfig.class)
                    .error("【安全告警】auth.security.local-debug=true，/api/sys/** 全放行并按内置 admin 处理，禁止用于生产！");
            http.csrf().disable()
                    .requestMatchers().antMatchers("/api/sys/**")
                    .and().authorizeRequests().antMatchers("/api/sys/**").permitAll();
            return;
        }
        http.csrf().disable()
                .requestMatchers().antMatchers("/api/sys/**")
                .and().authorizeRequests().antMatchers("/api/sys/**").access("#oauth2.hasScope('read')");
    }

    /**
     * OAuth2 上下文过滤器：SecurityContext → CurrentUserContext（审计/权限拦截器取值）。
     * local-debug 下无认证时填内置 admin（ROLE_ADMIN）。
     */
    @org.springframework.stereotype.Component
    @org.springframework.core.annotation.Order(2)
    public static class OAuth2ContextFilter extends HttpFilter {

        @Value("${auth.security.local-debug:true}")
        private boolean localDebug;

        @Override
        protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean filled = false;
            if (auth instanceof OAuth2Authentication) {
                OAuth2Authentication oauth = (OAuth2Authentication) auth;
                if (!oauth.isClientOnly() && oauth.getUserAuthentication().getPrincipal()
                        instanceof com.server.oauth.LowcodeUserDetails) {
                    com.server.oauth.LowcodeUserDetails user =
                            (com.server.oauth.LowcodeUserDetails) oauth.getUserAuthentication().getPrincipal();
                    fill(user.getUserId(), user.getUsername(), authorities(oauth));
                    filled = true;
                } else if (!oauth.isClientOnly()) {
                    fill(null, oauth.getName(), authorities(oauth));
                    filled = true;
                }
            }
            if (!filled && localDebug && auth == null && req.getRequestURI() != null
                    && req.getRequestURI().startsWith("/api/sys/")) {
                fill(CurrentUserContext.DEBUG_ADMIN_ID, CurrentUserContext.DEBUG_ADMIN_NAME,
                        java.util.Collections.singletonList("ROLE_ADMIN"));
                filled = true;
            }
            try {
                chain.doFilter(req, res);
            } finally {
                CurrentUserContext.clear();
                SecurityContextHolder.clearContext();
            }
        }

        private List<String> authorities(OAuth2Authentication oauth) {
            List<String> list = new ArrayList<>();
            for (GrantedAuthority a : oauth.getAuthorities()) {
                list.add(a.getAuthority());
            }
            return list;
        }

        private void fill(String uid, String uname, List<String> authorities) {
            List<String> roles = new ArrayList<>();
            List<String> perms = new ArrayList<>();
            for (String a : authorities) {
                if (a.startsWith("ROLE_")) {
                    roles.add(a.substring(5));
                } else {
                    perms.add(a);
                }
            }
            CurrentUserContext.set(uid, uname, roles, perms);
        }
    }
}