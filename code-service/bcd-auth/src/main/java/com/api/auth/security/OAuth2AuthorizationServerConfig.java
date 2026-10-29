package com.api.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.oauth2.provider.client.InMemoryClientDetailsService;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;

import javax.annotation.Resource;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 授权服务器（spring-security-oauth2 2.3.7）：
 * - 客户端 webapp（password / refresh_token 授权，secret BCrypt 存储）
 * - TokenStore：RedisTokenStore（prefix auth: + 共享 DeviceAuthenticationKeyGenerator）
 *   ——登出/踢人删 token 即失效（bcd-system 资源服务器查同一 store 校验）
 * - Access Token：RS256 JWT（jwt.jks 密钥对；bcd-gateway 持 public.key 公钥本地验签）
 * - Refresh Token：opaque 存 Redis（7d）
 * - 认证索引 key 纳入 deviceId（多端并存、登出仅清当前设备）
 */
@Configuration
@EnableAuthorizationServer
public class OAuth2AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Value("${auth.oauth.client-id:webapp}")
    private String clientId;

    @Value("${auth.oauth.client-secret:webapp}")
    private String clientSecret;

    @Value("${auth.oauth.access-token-validity-seconds:7200}")
    private int accessValidity;

    @Value("${auth.oauth.refresh-token-validity-seconds:604800}")
    private int refreshValidity;

    @Value("${auth.oauth.keystore:jwt.jks}")
    private String keystore;

    @Value("${auth.oauth.keystore-alias:jwt}")
    private String keystoreAlias;

    @Value("${auth.oauth.keystore-password:bcdjwt2026}")
    private String keystorePassword;

    @Bean
    public TokenStore tokenStore() {
        RedisTokenStore store = new RedisTokenStore(redisConnectionFactory);
        store.setPrefix("auth:");
        store.setAuthenticationKeyGenerator(new com.server.oauth.DeviceAuthenticationKeyGenerator());
        // 定向放行白名单版 JDK 序列化（默认策略拒绝平台自定义 principal 类反序列化）
        store.setSerializationStrategy(new com.server.oauth.PlatformTokenSerializationStrategy());
        return store;
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        KeyPair keyPair = new KeyStoreKeyFactory(
                new ClassPathResource(keystore), keystorePassword.toCharArray()).getKeyPair(keystoreAlias);
        converter.setKeyPair(keyPair);
        return converter;
    }

    /** JWT claims 增强：uid / uname / roles（逗号） / deviceId（网关透传 X-User-* 用） */
    @Bean
    public TokenEnhancer customTokenEnhancer() {
        return (accessToken, authentication) -> {
            if (authentication.isClientOnly()) {
                return accessToken;
            }
            Object principal = authentication.getUserAuthentication().getPrincipal();
            Map<String, Object> extra = new HashMap<>();
            if (principal instanceof com.server.oauth.LowcodeUserDetails) {
                com.server.oauth.LowcodeUserDetails user = (com.server.oauth.LowcodeUserDetails) principal;
                extra.put("uid", user.getUserId());
                extra.put("uname", user.getUsername());
                extra.put("roles", String.join(",", user.getRoleCodes()));
            } else {
                extra.put("uname", authentication.getName());
                extra.put("roles", "");
            }
            String deviceId = authentication.getOAuth2Request().getRequestParameters().get("deviceId");
            extra.put("deviceId", deviceId == null ? "" : deviceId);
            if (accessToken instanceof DefaultOAuth2AccessToken) {
                ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(extra);
            }
            return accessToken;
        };
    }

    @Bean
    public DefaultTokenServices defaultTokenServices() {
        DefaultTokenServices services = new DefaultTokenServices();
        services.setTokenStore(tokenStore());
        TokenEnhancerChain chain = new TokenEnhancerChain();
        chain.setTokenEnhancers(Arrays.asList(customTokenEnhancer(), jwtAccessTokenConverter()));
        services.setTokenEnhancer(chain);
        services.setSupportRefreshToken(true);
        services.setReuseRefreshToken(true);
        services.setAccessTokenValiditySeconds(accessValidity);
        services.setRefreshTokenValiditySeconds(refreshValidity);
        return services;
    }

    @Bean
    public InMemoryClientDetailsService lowcodeClientDetailsService() {
        BaseClientDetails client = new BaseClientDetails();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setAuthorizedGrantTypes(Arrays.asList("password", "refresh_token"));
        client.setScope(Arrays.asList("read", "write"));
        client.setAutoApproveScopes(Arrays.asList("read", "write"));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "bcd-lowcode-auth");
        client.setAdditionalInformation(info);

        InMemoryClientDetailsService service = new InMemoryClientDetailsService();
        Map<String, ClientDetails> detailsMap = new HashMap<>();
        detailsMap.put(clientId, client);
        service.setClientDetailsStore(detailsMap);
        return service;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(lowcodeClientDetailsService());
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        endpoints
                .authenticationManager(authenticationManager)
                .tokenStore(tokenStore())
                .tokenEnhancer(jwtAccessTokenConverter())
                .tokenServices(defaultTokenServices());
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) {
        // check_token 端点供网关在线校验（客户端 Basic 认证）
        security.checkTokenAccess("isAuthenticated()")
                .allowFormAuthenticationForClients();
    }
}