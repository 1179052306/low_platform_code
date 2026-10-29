package com.api.auth.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.auth.security.LoginGuard;
import com.api.auth.service.SessionService;
import com.common.returns.ResMsg;
import com.server.oauth.LowcodeUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 认证包装端点（内部走 OAuth2 DefaultTokenServices；标准 /oauth/token 端点仍可供第三方调用）：
 * POST /api/auth/login   {username, password, deviceId} → 双令牌+用户+权限码
 * POST /api/auth/refresh {refreshToken, deviceId}      → 新 access/refresh
 * POST /api/auth/logout  （Bearer 头）                  → 删当前设备 token（RedisTokenStore 删即失效）
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private DefaultTokenServices tokenServices;

    @Resource
    private TokenStore tokenStore;

    @Resource
    private LoginGuard loginGuard;

    @Resource
    private SessionService sessionService;

    @Value("${auth.oauth.client-id:webapp}")
    private String clientId;

    @PostMapping("/login")
    public ResMsg login(@RequestBody JSONObject body, HttpServletRequest req) {
        ResMsg r = new ResMsg();
        String username = body.getString("username");
        String password = body.getString("password");
        String deviceId = body.getString("deviceId");
        String ip = clientIp(req);
        try {
            if (username == null || password == null) {
                throw new IllegalArgumentException("用户名与密码不能为空");
            }
            if (loginGuard.isLocked(username, ip)) {
                throw new IllegalStateException("账号已锁定，请 10 分钟后重试");
            }
            Authentication auth;
            try {
                auth = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(username, password));
            } catch (BadCredentialsException | InternalAuthenticationServiceException e) {
                boolean locked = loginGuard.recordFail(username, ip);
                throw new IllegalStateException(locked ? "失败次数过多，账号已锁定 10 分钟" : "用户名或密码错误");
            } catch (DisabledException e) {
                throw new IllegalStateException("账号已被禁用");
            }
            loginGuard.clear(username, ip);

            LowcodeUserDetails user = (LowcodeUserDetails) auth.getPrincipal();
            OAuth2Authentication oauth2 = buildOAuth2Authentication(auth, deviceId);
            // 同设备旧会话顶替（重登=新令牌）
            OAuth2AccessToken existing = tokenStore.getAccessToken(oauth2);
            if (existing != null) {
                sessionService.evictSession(existing.getValue());
            }
            OAuth2AccessToken token = tokenServices.createAccessToken(oauth2);

            String userId = user.getUserId();
            List<String> roles = user.getRoleCodes();
            List<String> perms = user.getPermCodes();

            sessionService.saveOnline(userId, deviceId, token.getValue(),
                    token.getExpiration().getTime(), ip, req.getHeader("User-Agent"), user.getUsername());
            sessionService.updateLastLogin(userId);
            sessionService.writeLoginLog(userId, user.getUsername(), deviceId, ip, true, "登录成功");

            JSONObject result = new JSONObject();
            result.put("accessToken", token.getValue());
            result.put("refreshToken", token.getRefreshToken().getValue());
            result.put("expiresIn", token.getExpiresIn());
            JSONObject u = new JSONObject();
            u.put("id", userId);
            u.put("username", user.getUsername());
            u.put("realName", user.getRealName());
            u.put("roles", new JSONArray(roles));
            result.put("user", u);
            result.put("permCodes", new JSONArray(perms));
            r.setData(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            sessionService.writeLoginLog(null, username, deviceId, ip, false, e.getMessage());
            r.setRes(false);
            r.setCode("401");
            r.setMsg(e.getMessage());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg("登录失败：" + e.getMessage());
        }
        return r;
    }

    @PostMapping("/refresh")
    public ResMsg refresh(@RequestBody JSONObject body) {
        ResMsg r = new ResMsg();
        try {
            String refreshTokenValue = body.getString("refreshToken");
            if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
                throw new IllegalArgumentException("refreshToken 不能为空");
            }
            Map<String, String> params = new HashMap<>();
            String deviceId = body.getString("deviceId");
            if (deviceId != null && !deviceId.isEmpty()) {
                params.put("deviceId", deviceId);
            }
            org.springframework.security.oauth2.provider.TokenRequest tr =
                    new org.springframework.security.oauth2.provider.TokenRequest(
                            params, clientId, new HashSet<>(Arrays.asList("read", "write")), "refresh_token");
            OAuth2AccessToken token = tokenServices.refreshAccessToken(refreshTokenValue, tr);
            JSONObject result = new JSONObject();
            result.put("accessToken", token.getValue());
            result.put("refreshToken", token.getRefreshToken() != null ? token.getRefreshToken().getValue() : refreshTokenValue);
            result.put("expiresIn", token.getExpiresIn());
            r.setData(result);
        } catch (InvalidGrantException | IllegalArgumentException | IllegalStateException e) {
            r.setRes(false);
            r.setCode("401");
            r.setMsg("刷新令牌无效或已过期");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg("刷新失败：" + e.getMessage());
        }
        return r;
    }

    @PostMapping("/logout")
    public ResMsg logout(HttpServletRequest req) {
        String tokenValue = extractBearer(req);
        sessionService.evictSession(tokenValue);
        ResMsg r = new ResMsg();
        r.setData("已登出");
        return r;
    }

    /** 第三方登录回调占位（P4 实现各平台 Provider 后启用） */
    @PostMapping("/third/{type}/callback")
    public ResMsg thirdCallback(@PathVariable String type) {
        ResMsg r = new ResMsg();
        r.setRes(false);
        r.setCode("501");
        r.setMsg("第三方登录 [" + type + "] 尚未配置，请先在 auth 侧配置对应平台参数");
        return r;
    }

    @GetMapping("/captcha")
    public ResMsg captcha() {
        ResMsg r = new ResMsg();
        r.setRes(false);
        r.setCode("501");
        r.setMsg("验证码尚未启用");
        return r;
    }

    private OAuth2Authentication buildOAuth2Authentication(Authentication auth, String deviceId) {
        Map<String, String> params = new HashMap<>();
        if (deviceId != null && !deviceId.isEmpty()) {
            params.put("deviceId", deviceId);
        }
        Set<String> scopes = new HashSet<>(Arrays.asList("read", "write"));
        Set<GrantedAuthority> authorities = new HashSet<>(auth.getAuthorities());
        OAuth2Request request = new OAuth2Request(params, clientId, authorities, true, scopes,
                Collections.singleton("lowcode"), null, null, Collections.emptyMap());
        UsernamePasswordAuthenticationToken userAuth = (UsernamePasswordAuthenticationToken) auth;
        return new OAuth2Authentication(request, userAuth);
    }

    private String extractBearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7);
        }
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : req.getRemoteAddr();
    }
}