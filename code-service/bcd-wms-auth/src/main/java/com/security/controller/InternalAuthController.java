package com.security.controller;

import com.security.oauth2.config.DynamicClientDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部登录接口：供后端微服务通过 Feign 调用，
 * 直接校验账号密码并签发 JWT，跳过 OAuth2 授权码套娃流程。
 * 签出的 token 与 /oauth/token 走的是同一套 tokenService / JWT 增强逻辑
 * （JdbcAuthorizationServerConfig 里的 @Primary DefaultTokenServices +
 *  JwtAccessTokenConverter + TokenEnhancer）。
 *
 * 安全约束：
 *   1. 本接口仅供内部服务调用，已在 SpringSecurityConfig 放行 /internal/**
 *   2. 不应通过 gateway(8004) 暴露到公网，gateway 路由表里不要加 /internal/** 转发
 *   3. 生产环境建议补充内网 IP 白名单或内部 token 头校验
 *
 * @author lw
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    /**
     * JdbcAuthorizationServerConfig 里 @Primary 的 DefaultTokenServices Bean，
     * 与 /oauth/token 走的是同一个，签出的 token 完全一致。
     */
    @Autowired
    private AuthorizationServerTokenServices tokenService;

    @Autowired
    private DynamicClientDetailsService clientDetailsService;

    /**
     * 内部登录：校验账号密码 + 直接签 JWT。
     *
     * @param username 用户名
     * @param password 明文密码（调用方负责 RSA 解密后传入）
     * @param tenant   租户号；多租户场景作为 clientId，单租户/为空时兜底 "sysid"
     * @return Map 形如 {res, status, access_token, refresh_token, expires_in, token_type, scope, errmsg}
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam("username") String username,
                                     @RequestParam("password") String password,
                                     @RequestHeader(value = "tenant", required = false) String tenant) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 校验账号密码 —— 走 Spring Security 标准链，与 /login 表单登录用的是同一个 AuthenticationManager
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, password);
            Authentication auth = authenticationManager.authenticate(authToken);

            // 2. 加载 client —— 多租户场景 tenant 头作为 clientId；单租户/为空兜底 "sysid"
            String clientId = (tenant == null || tenant.isEmpty()) ? "sysid" : tenant;
            ClientDetails client = clientDetailsService.loadClientByClientId(clientId);

            // 3. 直接签 JWT —— 跳过授权码四步套娃，复用 tokenService.createAccessToken
            TokenRequest tokenRequest = new TokenRequest(null, clientId, client.getScope(), "internal");
            OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request(client);
            OAuth2Authentication oAuth2Auth = new OAuth2Authentication(oAuth2Request, auth);
            OAuth2AccessToken accessToken = tokenService.createAccessToken(oAuth2Auth);

            // 4. 返回跟 /oauth/token 一模一样的结构（额外加 res/status 便于调用方判断）
            result.put("access_token", accessToken.getValue());
            result.put("token_type", accessToken.getTokenType());
            if (accessToken.getRefreshToken() != null) {
                result.put("refresh_token", accessToken.getRefreshToken().getValue());
            }
            result.put("expires_in", accessToken.getExpiresIn());
            result.put("scope", String.join(" ", accessToken.getScope()));
            result.put("res", true);
            result.put("status", "success");
            return result;
        } catch (AuthenticationException ex) {
            // 账号或密码错误
            log.warn("内部登录失败，username={}, err={}", username, ex.getMessage());
            result.put("res", false);
            result.put("status", "fail");
            result.put("errmsg", "账号或密码错误");
            return result;
        } catch (Exception ex) {
            log.error("内部登录异常，username=" + username, ex);
            result.put("res", false);
            result.put("status", "fail");
            result.put("errmsg", "登录异常：" + ex.getMessage());
            return result;
        }
    }
}
