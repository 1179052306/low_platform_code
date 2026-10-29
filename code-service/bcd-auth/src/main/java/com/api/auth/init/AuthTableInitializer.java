package com.api.auth.init;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.db.MultiDataSource;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证侧初始化：db/sys_tables.sql（IF NOT EXISTS 幂等，DDL 走 JdbcTemplate——与 bcd-code
 * LowcodeTableInitializer 同模式）+ admin 用户 + ADMIN 角色（DML 全走 helper）。
 * 菜单树/ADMIN 授权初始化在 bcd-system 侧（SystemTableInitializer）。
 */
@Slf4j
@Component
@Order(1)
public class AuthTableInitializer implements CommandLineRunner {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private MultiDataSource multiDataSource;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Value("${auth.init.admin-username:admin}")
    private String adminUsername;

    @Value("${auth.init.admin-password:Admin@123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        executeDdl();
        initAdmin();
        log.info("bcd-auth 初始化完成（系统表/admin 用户/ADMIN 角色）");
    }

    private void executeDdl() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/sys_tables.sql")) {
            if (is == null) {
                throw new IllegalStateException("db/sys_tables.sql 不存在");
            }
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            com.server.db.MultiDataSourceHolder.setDatasource("lowcode");
            try {
                JdbcTemplate jdbc = new JdbcTemplate(multiDataSource);
                for (String stmt : sql.split(";")) {
                    String s = stripComments(stmt);
                    if (s.isEmpty()) {
                        continue;
                    }
                    jdbc.execute(s);
                }
            } finally {
                com.server.db.MultiDataSourceHolder.clearDataSource();
            }
        }
    }

    /** 剥离 -- 注释行（语句块可能以注释开头，不能按整块 startsWith 判断跳过） */
    private static String stripComments(String stmt) {
        StringBuilder sb = new StringBuilder();
        for (String line : stmt.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private void initAdmin() throws Exception {
        JSONArray users = helper.query("lc_user", new String[]{"id"}, null, null, false);
        if (!users.isEmpty()) {
            ensureAdminRole();
            return;
        }
        String now = java.time.Instant.now().toString();
        String adminId = uuid();
        JSONObject user = new JSONObject();
        user.put("id", adminId);
        user.put("username", adminUsername);
        user.put("password", passwordEncoder.encode(adminPassword));
        user.put("real_name", "系统管理员");
        user.put("status", "enabled");
        user.put("created_at", now);
        user.put("updated_at", now);
        SqlEngineJson.check(helper.insert("lc_user", user));
        ensureAdminRole();
        JSONObject userRole = new JSONObject();
        userRole.put("id", uuid());
        userRole.put("user_id", adminId);
        userRole.put("role_code", "ADMIN");
        userRole.put("created_at", now);
        SqlEngineJson.check(helper.insert("lc_user_role", userRole));
        log.warn("已创建初始管理员：{} / {}（首次登录后请尽快修改密码）", adminUsername, adminPassword);
    }

    private void ensureAdminRole() throws Exception {
        JSONArray roles = helper.query("lc_role", new String[]{"code"},
                helper.eq("code", "ADMIN"), null, false);
        if (!roles.isEmpty()) {
            return;
        }
        JSONObject role = new JSONObject();
        role.put("id", uuid());
        role.put("code", "ADMIN");
        role.put("name", "系统管理员");
        role.put("enabled", true);
        role.put("created_at", java.time.Instant.now().toString());
        SqlEngineJson.check(helper.insert("lc_role", role));
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}