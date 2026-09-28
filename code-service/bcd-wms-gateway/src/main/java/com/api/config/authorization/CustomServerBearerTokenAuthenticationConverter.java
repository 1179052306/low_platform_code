package com.api.config.authorization;

import com.alibaba.fastjson2.JSONObject;
import com.server.db.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用于将请求转换为Authentication对象。它负责从请求中提取认证信息（如令牌、用户名和密码等）并创建相应的Authentication对象，以便后续进行身份验证。
 */
@Slf4j
@Configuration
@Service
public class CustomServerBearerTokenAuthenticationConverter implements ServerAuthenticationConverter {

    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);

    private boolean allowUriQueryParameter = false;

    private String bearerTokenHeaderName = HttpHeaders.AUTHORIZATION;

    @Resource
    private WhitelistValidate whitelistValidate;

    @Resource
    private MobileVersionValidate mobileVersionValidate;
    @Resource
    private ClientVersionValidate clientVersionValidate;
    @Resource
    Tenant tenant;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        if (exchange.getRequest().getPath().toString().contains("/actuator")) {
            return Mono.empty();
        }
        if (exchange.getRequest().getPath().toString().contains("/ws")) {
            return Mono.empty();
        }
        if (exchange.getRequest().getPath().toString().contains("/pdasocket")) {
            return Mono.empty();
        }
        if (exchange.getRequest().getPath().toString().contains("/websocket")) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> token(exchange.getRequest())).map((token) -> {
            if (token.isEmpty()) {
                BearerTokenError error = invalidTokenError();
                throw new OAuth2AuthenticationException(error);
            }

            return new BearerTokenAuthenticationToken(token);
        });


    }

    private String token(ServerHttpRequest request) throws Exception {
        String authorizationHeaderToken = resolveFromAuthorizationHeader(request.getHeaders());

        if (request.getURI().getPath().equals("/system/pdaUpload")) {
            String tenantId = "";
            if (!tenant.getSingle()) {
                tenantId = request.getHeaders().getFirst("tenant");
            }
            mobileVersionValidate.setdbValidate(tenantId);
        }
        if(request.getURI().getPath().equals("/system/clientUpload")){
            String tenantId = "";
            if (!tenant.getSingle()) {
                tenantId = request.getHeaders().getFirst("tenant");
            }
            clientVersionValidate.setdbValidate(tenantId);
        }
        if (authorizationHeaderToken == null) {

            if (!whitelistValidate.redisValidate(request.getURI().getPath())) {
                BearerTokenError error = BearerTokenErrors.invalidRequest("请求中包含的令牌为空，不允许请求！");
                throw new OAuth2AuthenticationException(error);
            }


        } else {
            String request_type = request.getHeaders().getFirst("request_type");
            if (request_type != null) {
                if (request_type.equals("1")) {
                    JSONObject mobileVersionMode = new JSONObject();
                    if (!version(request, mobileVersionMode)) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("426", mobileVersionMode.toString(), ""));
                    }

                }
                if (request_type.equals("2")) {
                    JSONObject clientVersionMode=new JSONObject();
                    if (!clientversion(request,clientVersionMode)) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("426", clientVersionMode.toString(), ""));
                    }

                }
            } else {

                String requestType = request.getHeaders().getFirst("requestType");

                if (requestType != null) {
                    if (requestType.equals("1")) {
                        JSONObject mobileVersionMode = new JSONObject();
                        if (!version(request, mobileVersionMode)) {
                            throw new OAuth2AuthenticationException(new OAuth2Error("426", mobileVersionMode.toString(), ""));
                        }

                    }
                    if (requestType.equals("2")) {
                        JSONObject clientVersionMode=new JSONObject();
                        if (!clientversion(request,clientVersionMode)) {
                            throw new OAuth2AuthenticationException(new OAuth2Error("426", clientVersionMode.toString(), ""));
                        }

                    }
                }
            }

        }

        return authorizationHeaderToken;


    }

    private Boolean version(ServerHttpRequest request, JSONObject mobileVersionMode) {
        if (request.getURI().getPath().equals("/system/getKey")) {
            return true;
        }
        String mobileVersion = request.getHeaders().getFirst("version_code");
        String tenantId = "";
        if (!tenant.getSingle()) {
            tenantId = request.getHeaders().getFirst("tenant");
        }

//        if (!mobileVersionValidate.redisValidate(mobileVersion, mobileVersionMode, tenantId)) {
//            return false;
//        }
        if (mobileVersion != null) {
            if (!mobileVersionValidate.redisValidate(mobileVersion, mobileVersionMode, tenantId)) {
                return false;
            }
        } else {
            String mobileVersionCode = request.getHeaders().getFirst("versionCode");
            if (!mobileVersionValidate.redisValidate(mobileVersionCode, mobileVersionMode, tenantId)) {
                return false;
            }
        }


        return true;


    }

    private Boolean clientversion(ServerHttpRequest request, JSONObject mobileVersionMode) {
        if (request.getURI().getPath().equals("/system/getKey")) {
            return true;
        }
        String mobileVersion = request.getHeaders().getFirst("version_code");
        String tenantId = "";
        if (!tenant.getSingle()) {
            tenantId = request.getHeaders().getFirst("tenant");
        }

        if (!clientVersionValidate.redisValidate(mobileVersion, mobileVersionMode, tenantId)) {
            return false;
        } else {
            String mobileVersionCode = request.getHeaders().getFirst("version_code");
            if (!clientVersionValidate.redisValidate(mobileVersionCode, mobileVersionMode, tenantId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Set if transport of access token using URI query parameter is supported. Defaults
     * to {@code false}.
     * <p>
     * The spec recommends against using this mechanism for sending bearer tokens, and
     * even goes as far as stating that it was only included for completeness.
     *
     * @param allowUriQueryParameter if the URI query parameter is supported
     */
    public void setAllowUriQueryParameter(boolean allowUriQueryParameter) {
        this.allowUriQueryParameter = allowUriQueryParameter;
    }

    /**
     * Set this value to configure what header is checked when resolving a Bearer Token.
     * This value is defaulted to {@link HttpHeaders#AUTHORIZATION}.
     * <p>
     * This allows other headers to be used as the Bearer Token source such as
     * {@link HttpHeaders#PROXY_AUTHORIZATION}
     *
     * @param bearerTokenHeaderName the header to check when retrieving the Bearer Token.
     * @since 5.4
     */
    public void setBearerTokenHeaderName(String bearerTokenHeaderName) {
        this.bearerTokenHeaderName = bearerTokenHeaderName;
    }

    private String resolveFromAuthorizationHeader(HttpHeaders headers) {
        String authorization = headers.getFirst(this.bearerTokenHeaderName);
        if (!StringUtils.startsWithIgnoreCase(authorization, "bearer")) {
            return null;
        }
        Matcher matcher = AUTHORIZATION_PATTERN.matcher(authorization);
        if (!matcher.matches()) {
            BearerTokenError error = invalidTokenError();
            throw new OAuth2AuthenticationException(error);
        }
        return matcher.group("token");
    }

    private static BearerTokenError invalidTokenError() {
        return BearerTokenErrors.invalidToken("Bearer token is malformed");
    }

    private boolean isParameterTokenSupportedForRequest(ServerHttpRequest request) {
        return this.allowUriQueryParameter && HttpMethod.GET.equals(request.getMethod());
    }
}
