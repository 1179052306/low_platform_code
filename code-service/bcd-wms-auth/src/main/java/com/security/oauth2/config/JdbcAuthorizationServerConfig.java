package com.security.oauth2.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.provider.token.*;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableAuthorizationServer
public class JdbcAuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {


    private final AuthenticationManager authenticationManager;

    private final DynamicClientDetailsService dynamicClientDetailsService;

    public JdbcAuthorizationServerConfig(PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, DynamicClientDetailsService dynamicClientDetailsService) {
        this.authenticationManager = authenticationManager;
        this.dynamicClientDetailsService = dynamicClientDetailsService;
    }

    /**
     * 配置从数据库中加载客户端信息
     */
//    @Bean
//    public ClientDetailsService clientDetails() {
//        JdbcClientDetailsService jdbcClientDetailsService = new JdbcClientDetailsService(dataSource);
//        jdbcClientDetailsService.setPasswordEncoder(passwordEncoder);
//        jdbcClientDetailsService.listClientDetails();
//        return jdbcClientDetailsService;
//    }

    /**
     * 配置授权码存储到数据库
     */
//    @Bean
//    public AuthorizationCodeServices authorizationCodeServices() {
//        return new JdbcAuthorizationCodeServices(dataSource);
//    }

    //region jwt相关

    /**
     * 从classpath下的密钥库中获取密钥对(公钥+私钥)
     */
    @Bean
    public KeyPair keyPair() {
        KeyStoreKeyFactory factory = new KeyStoreKeyFactory(new ClassPathResource("jwt.jks"), "Ahbcd0306".toCharArray());
        return factory.getKeyPair("bcd", "Ahbcd0306".toCharArray());
    }

    /**
     * JWT内容增强
     * 在jwt的载荷中加入自定义的一些内容
     */
    @Bean
    public TokenEnhancer tokenEnhancer() {
        return (accessToken, authentication) -> {
            Map<String, Object> map = new HashMap<>();
            User user = (User) authentication.getUserAuthentication().getPrincipal();
            map.put("user_key", user.getUserKey());
            map.put("user_id", user.getUsername());
            map.put("user_name", user.getUserNameDesc());
            map.put("jurisdiction", user.getJurisdiction());
            map.put("usertype", user.getUserType());
            map.put("supplier_key", user.getSupplierKey());
            map.put("activationinfo", user.getActivationInfo());
            map.put("tenant", user.getTenantId());
            ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(map);
            return accessToken;
        };
    }

    /**
     * 配置令牌存储
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    @Primary
    @Bean
    public AuthorizationServerTokenServices tokenService() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
//        defaultTokenServices.setAccessTokenValiditySeconds(7200); // 令牌默认有效期2小时
//        defaultTokenServices.setRefreshTokenValiditySeconds(259200); // 刷新令牌默认有效期3天
//        defaultTokenServices.setClientDetailsService(clientDetails());//客户端详情服务
        defaultTokenServices.setSupportRefreshToken(true);//支持刷新令牌
        defaultTokenServices.setTokenStore(tokenStore());//令牌存储策略

        //令牌转由普通令牌转换为JWT
        TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
        List<TokenEnhancer> enhancers = new ArrayList<>();
        enhancers.add(tokenEnhancer());//自定义JWT模板
        enhancers.add(jwtAccessTokenConverter());
        tokenEnhancerChain.setTokenEnhancers(enhancers);

        //令牌转由普通令牌转换为JWT
        defaultTokenServices.setTokenEnhancer(tokenEnhancerChain);
        return defaultTokenServices;
    }

    /**
     * 使用同一个秘钥来编码 JWT 中的 oauth2 令牌
     */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {

        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setKeyPair(keyPair());

        return converter;
    }
    //endregion

    /**
     * 配置客户端信息
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(dynamicClientDetailsService);
//        clients.inMemory()
//                .withClient("sysid")
//                .scopes("all")
//                .secret("e10adc3949ba59abbe56e057f20f883e")
//                .authorizedGrantTypes("authorization_code", "refresh_token")
//                .redirectUris("http://www.baidu.com");

    }


    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        //认证管理器
        endpoints.authenticationManager(authenticationManager)
                .tokenStore(tokenStore())
                //.authorizationCodeServices(authorizationCodeServices())
                .tokenServices(tokenService())
                .setClientDetailsService(dynamicClientDetailsService);
    }
}
