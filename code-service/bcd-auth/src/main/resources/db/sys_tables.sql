-- ============================================================
-- 系统管理 RBAC 表（bcd-auth）PostgreSQL
-- 全部 IF NOT EXISTS 幂等；id VARCHAR(64) UUID；时间列 VARCHAR(64) ISO-8601
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_user (
    id            VARCHAR(64) PRIMARY KEY,
    username      VARCHAR(128) NOT NULL,
    password      VARCHAR(128) NOT NULL,
    real_name     VARCHAR(128),
    email         VARCHAR(128),
    mobile        VARCHAR(32),
    avatar        VARCHAR(512),
    status        VARCHAR(16) DEFAULT 'enabled',
    last_login_at VARCHAR(64),
    created_at    VARCHAR(64),
    updated_at    VARCHAR(64)
);

-- 大小写不敏感唯一（Admin/admin 不允许为两个账号）
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username_lower ON lc_user (lower(username));

CREATE TABLE IF NOT EXISTS lc_user_role (
    id         VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    role_code  VARCHAR(128) NOT NULL,
    created_at VARCHAR(64),
    UNIQUE(user_id, role_code)
);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON lc_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON lc_user_role (role_code);

CREATE TABLE IF NOT EXISTS lc_menu (
    id              VARCHAR(64) PRIMARY KEY,
    parent_id       VARCHAR(64),
    menu_type       VARCHAR(16) NOT NULL,
    menu_name       VARCHAR(128) NOT NULL,
    name_key        VARCHAR(255),
    menu_code       VARCHAR(128) NOT NULL UNIQUE,
    route_path      VARCHAR(255),
    component       VARCHAR(255),
    permission_code VARCHAR(128),
    icon            VARCHAR(64),
    sort_no         INTEGER DEFAULT 0,
    visible         BOOLEAN DEFAULT TRUE,
    status          VARCHAR(16) DEFAULT 'enabled',
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_menu_parent ON lc_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_menu_perm ON lc_menu (permission_code);

CREATE TABLE IF NOT EXISTS lc_role_menu (
    id         VARCHAR(64) PRIMARY KEY,
    role_code  VARCHAR(128) NOT NULL,
    menu_id    VARCHAR(64) NOT NULL,
    created_at VARCHAR(64),
    UNIQUE(role_code, menu_id)
);
CREATE INDEX IF NOT EXISTS idx_role_menu_role ON lc_role_menu (role_code);

CREATE TABLE IF NOT EXISTS lc_user_identity (
    id                VARCHAR(64) PRIMARY KEY,
    user_id           VARCHAR(64) NOT NULL,
    identity_type     VARCHAR(32) NOT NULL,
    external_user_id  VARCHAR(255) NOT NULL,
    external_username VARCHAR(255),
    bound_at          VARCHAR(64),
    UNIQUE(identity_type, external_user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_identity_user ON lc_user_identity (user_id);

CREATE TABLE IF NOT EXISTS lc_login_log (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64),
    username    VARCHAR(128),
    login_type  VARCHAR(32) DEFAULT 'password',
    device_id   VARCHAR(64),
    success     BOOLEAN,
    message     VARCHAR(255),
    ip          VARCHAR(64),
    login_at    VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_login_log_time ON lc_login_log (login_at DESC);

CREATE TABLE IF NOT EXISTS lc_op_log (
    id            VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    username      VARCHAR(128),
    module        VARCHAR(64),
    action        VARCHAR(64),
    params_digest VARCHAR(1024),
    success       BOOLEAN,
    message       VARCHAR(255),
    ip            VARCHAR(64),
    operated_at   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_op_log_time ON lc_op_log (operated_at DESC);