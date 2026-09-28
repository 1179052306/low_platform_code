package com.security.oauth2.config;

import com.common.encryption.Md5PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

// 包含了 @Configuration 注解
@Configuration
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new SmartPasswordEncoder();
    }
    @Autowired
    public UserService UserService;

    @Bean
    public UserDetailsService userDetailsService(){
        return UserService;
    }

    @Override
    @Bean  //将内部 authorizationManager 暴露
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

          // 使用jwt,则无需使用原本的session管理
        http.csrf().disable().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .authorizeRequests()
                // actuator中的所有健康检查端点都放行,经测试,此处包含了上述几处oauth端点,直接放行
                .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
//                // 此处不加项目的context-path
                .antMatchers("/oauth/authorize").permitAll()
                .antMatchers("/oauth/token").permitAll()
                .antMatchers("/oauth/logout").permitAll()
                .antMatchers("/logout").permitAll()
                .antMatchers("/getPublicKey").permitAll()
                .antMatchers("/customerActivation/activation").permitAll()
                .antMatchers("/customerActivation/getActivationData").permitAll()
                // 内部登录接口：仅供后端微服务通过 Feign 调用，详见 InternalAuthController
                // 安全约束：gateway 路由表不要转发 /internal/**，生产环境建议补内网 IP 白名单或内部 token 头
                .antMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
                .and().formLogin()
                .and().csrf().disable();
    }
}
