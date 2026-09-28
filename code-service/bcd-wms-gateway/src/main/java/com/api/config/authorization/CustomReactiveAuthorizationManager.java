package com.api.config.authorization;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.model.RequestRes;
import com.server.db.Tenant;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.URI;
import java.util.stream.Collectors;

/**
 * 自定义授权管理器，判断用户是否有权限访问
 * 用于确定用户是否具有执行特定操作或访问特定资源的权限。它在处理授权方面起着关键作用，允许你自定义授权逻辑，以决定请求是否被授权访问。
 */
@Component
@Slf4j
public class CustomReactiveAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {


    @Resource
    private RequestValidate requestValidate;
    @Resource
    private WhitelistValidate whitelistValidate;
    @Resource
    private MobileVersionValidate mobileVersionValidate;
    @Resource
    private TokenTransferFilter tokenTransferFilter;

    @Resource
    Tenant tenant;
    
    @SneakyThrows
    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext authorizationContext) {
        ServerWebExchange exchange = authorizationContext.getExchange();
        ServerHttpRequest request = exchange.getRequest();
        //获取到访问的url
        String path = request.getURI().getPath();

        URI uri = request.getURI();
        if(path.contains("/actuator")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/ws")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/pdasocket")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/websocket")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/jsonExport")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/jsonImport")){
            return Mono.just(new AuthorizationDecision(true));
        }
        if(path.contains("/getExportData")){
            return Mono.just(new AuthorizationDecision(true));
        }
        //后续处理 获取登录账号和pageid做判断
        String pageId = "";
        String tenantId = request.getHeaders().getFirst("tenant");
        String request_type = "";
        //用于判断是否允许访问
        String authorities = "";
        RequestRes requestRes = new RequestRes();
        if (!path.equals("/system/pdaUpload")&&!path.equals("/system/clientUpload")) {


            //获取过滤请求的pageid做判断
            pageId = request.getHeaders().getFirst("pageid");
            request_type = request.getHeaders().getFirst("request_type");
            //获取登录用户名作为key读取redis缓存的菜单列表
            String jurisdiction = request.getHeaders().getFirst("JURISDICTION");

            if (jurisdiction == null || pageId == null) {
                if (whitelistValidate.redisValidate(path)) {
//                    throw new OAuth2AuthenticationException(new OAuth2Error("426", "", ""));
//                    if(request_type.equals("1")){
//                        String mobileVersion=request.getHeaders().getFirst("version");
//                        MobileVersionMode mobileVersionMode=new MobileVersionMode();
//                        mobileVersionMode.mobileVersion=mobileVersion;
//                        if(!mobileVersionValidate.redisValidate(mobileVersion,mobileVersionMode)){
//   return Mono.defer(() -> Mono.just(exchange.getResponse()))
//                    .flatMap(response -> {
//                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
//                        String body = "{\"code\":401,\"msg\":\"token不合法或过期\"}";
//                        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
//                        return response.writeWith(Mono.just(buffer))
//                                .doOnError(error -> DataBufferUtils.release(buffer));
//                    });
//                            return Mono.just(new AuthorizationDecision(false));
//                        }
//                    }


                    return Mono.just(new AuthorizationDecision(true));
                } else {
                    return Mono.just(new AuthorizationDecision(false));
                }
            }
            requestRes = new RequestRes(jurisdiction, pageId, path);
            if(!tenant.getSingle()){

                requestRes.setTenant(tenantId);
            }

            boolean res = requestValidate.redisValidate(requestRes);
            if (!res) {

//                if(whitelistValidate.redisValidate( uri.getPath())){
//                    return Mono.just(new AuthorizationDecision(true));
//                }
                return Mono.just(new AuthorizationDecision(false));
            }
        }
        authorities = "all";
        //authorities = "all";
        log.info("访问路径:[{}],所需要的权限是:[{}]", path, authorities);

        String finalAuthorities = authorities;
        RequestRes finalRequestRes = requestRes;
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(a -> a instanceof JwtAuthenticationToken)
                .flatMap(auth -> {
                    try {
                        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
                        if(!tenant.getSingle()){
                           String tokenTenantId= jwtAuth.getTokenAttributes().get("tenant").toString();
                           if(StringUtils.isBlank(tokenTenantId)){
                               throw new OAuth2AuthenticationException(new OAuth2Error("430", "用户信息中不存在租户ID", ""));
                           }
                           if(!tokenTenantId.equals(tenantId)){
                               throw new OAuth2AuthenticationException(new OAuth2Error("430", "用户信息中租户ID和请求ID不相符", ""));
                           }
                        }
                        if (!("0".equals(finalRequestRes.getSystemType()))) {
                            String activationInfo = jwtAuth.getTokenAttributes().get("activationinfo").toString();
                            JSONArray data = JSONArray.parseArray(activationInfo);
                            if (data.size() == 0) {
                                throw new OAuth2AuthenticationException(new OAuth2Error("428", finalRequestRes.getSystemType(), ""));
                            } else {
                                JSONArray activationData = new JSONArray(data.stream().filter(item -> ((JSONObject) item).getString("BUYS_SYSTEM").equals(finalRequestRes.getSystemType())).collect(Collectors.toList()));
                                if (activationData.size() == 0) {
                                    throw new OAuth2AuthenticationException(new OAuth2Error("428", finalRequestRes.getSystemType(), ""));
                                }
                                JSONObject activation = activationData.getJSONObject(0);
                                if (activation.get("EXPIRE_FLAG").equals("1")) {
                                    throw new OAuth2AuthenticationException(new OAuth2Error("427", finalRequestRes.getSystemType(), ""));
                                }
                            }
                        }
                        return Mono.just(new AuthorizationDecision(true));

                    } catch (OAuth2AuthenticationException exception) {
                        throw exception; // 继续传播异常以便全局处理
                    } catch (Exception e) {
                        // 其他异常处理
                        return Mono.just(new AuthorizationDecision(false));
                    }
                })
//                .cast(JwtAuthenticationToken.class)
//                .doOnNext(token -> {
////                    token.getTokenAttributes().get("");
//                    System.out.println(token);
//                    System.out.println(token.getToken().getHeaders());
//                    System.out.println(token.getTokenAttributes());
//                }).flatMapIterable(AbstractAuthenticationToken::getAuthorities)
//                .map(GrantedAuthority::getAuthority)
//                .any(authority -> Objects.equals(authority, finalAuthorities))
//                .map(AuthorizationDecision::new)
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

}