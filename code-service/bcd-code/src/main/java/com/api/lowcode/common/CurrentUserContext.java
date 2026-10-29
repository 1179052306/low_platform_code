package com.api.lowcode.common;

import java.util.Arrays;

/**
 * 全平台唯一用户上下文来源（B10 契约）：
 * 审计 created_by / 导入 createdBy / 工作流 initiator 一律从此取值。
 * 值来自网关注入的 X-User-* 可信头（经 X-Gw-Sign 签名校验）；
 * local-debug=true 时无签名请求按内置 admin 处理（生产必须 false）。
 * 异步线程需任务落库 createdBy 后显式 set，finally 清理（禁 InheritableThreadLocal）。
 */
public class CurrentUserContext {

    public static final String DEBUG_ADMIN_ID = "debug-admin";
    public static final String DEBUG_ADMIN_NAME = "local-debug";

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    public static class Ctx {
        public final String userId;
        public final String userName;
        public final String[] roleCodes;

        public Ctx(String userId, String userName, String roleCodesCsv) {
            this.userId = userId;
            this.userName = userName;
            this.roleCodes = (roleCodesCsv == null || roleCodesCsv.isEmpty())
                    ? new String[0] : roleCodesCsv.split(",");
        }

        public boolean hasRole(String code) {
            return Arrays.asList(roleCodes).contains(code);
        }

        public boolean isAdmin() {
            return hasRole("ADMIN");
        }
    }

    public static void set(String userId, String userName, String roleCodesCsv) {
        HOLDER.set(new Ctx(userId, userName, roleCodesCsv));
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

    public static String[] roleCodes() {
        Ctx c = HOLDER.get();
        return c == null ? new String[0] : c.roleCodes;
    }

    public static void clear() {
        HOLDER.remove();
    }
}