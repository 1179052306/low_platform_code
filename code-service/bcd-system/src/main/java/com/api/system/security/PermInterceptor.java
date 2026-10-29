package com.api.system.security;

import com.api.system.common.CurrentUserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** @RequirePerm 校验：CurrentUserContext.perms 含权限码或 ADMIN；local-debug 内置 admin 放行 */
@Component
public class PermInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        RequirePerm ann = ((HandlerMethod) handler).getMethodAnnotation(RequirePerm.class);
        if (ann == null) {
            return true;
        }
        CurrentUserContext.Ctx ctx = CurrentUserContext.get();
        if (ctx == null) {
            send403(res, "未获取到用户上下文");
            return false;
        }
        if (ctx.isAdmin()) {
            return true;
        }
        if (ctx.perms.contains(ann.value())) {
            return true;
        }
        send403(res, "无操作权限：" + ann.value());
        return false;
    }

    private void send403(HttpServletResponse res, String msg) throws Exception {
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"res\":false,\"code\":\"403\",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
