package com.api.system.common;

import java.util.Arrays;
import java.util.List;

/**
 * 用户上下文（system 服务内由 OAuth2ContextFilter 从 SecurityContext 填充）。
 * lc_op_log.user_id 等审计取当前用户一律从此处取。
 */
public class CurrentUserContext {

    public static final String DEBUG_ADMIN_ID = "debug-admin";
    public static final String DEBUG_ADMIN_NAME = "local-debug";

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    public static class Ctx {
        public final String userId;
        public final String userName;
        public final List<String> roles;
        public final List<String> perms;

        public Ctx(String userId, String userName, List<String> roles, List<String> perms) {
            this.userId = userId;
            this.userName = userName;
            this.roles = roles == null ? Arrays.asList() : roles;
            this.perms = perms == null ? Arrays.asList() : perms;
        }

        public boolean isAdmin() {
            return roles.contains("ADMIN");
        }
    }

    public static void set(String userId, String userName, List<String> roles, List<String> perms) {
        HOLDER.set(new Ctx(userId, userName, roles, perms));
    }

    public static Ctx get() {
        return HOLDER.get();
    }

    public static String userId() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.userId;
    }

    public static String userName() {
        Ctx c = HOLDER.get();
        return c == null ? null : c.userName;
    }

    public static void clear() {
        HOLDER.remove();
    }
}