package com.server.tenant_http;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class TenantHttpContext {

    public static HttpServletRequest getRequest() {
        return getRequestAttributes().getRequest();
    }

    public static HttpServletResponse getResponse() {
        return getRequestAttributes().getResponse();
    }

    public static HttpSession getSession() {
        return getRequest().getSession();
    }

    public static ServletRequestAttributes getRequestAttributes() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
    }

    public static String getRequestValues(String Key) {


        if (getRequest().getParameterMap().get(Key) != null) {
            return getRequest().getParameterMap().get(Key)[0];
        } else {
            return null;
        }

    }
    public static String getHeader(String headerName) throws UnsupportedEncodingException {

        if (getRequest().getHeader(headerName)!= null) {
            return URLDecoder.decode(getRequest().getHeader(headerName), "utf-8");
        } else {

            return null;
        }

    }
    public static ServletContext getServletContext() {
        return ContextLoader.getCurrentWebApplicationContext().getServletContext();
    }
}
