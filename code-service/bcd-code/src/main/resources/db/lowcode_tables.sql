-- ============================================================
-- 低代码平台元数据表 DDL（PostgreSQL）
-- 由 LowcodeTableInitializer 在启动时自动执行（IF NOT EXISTS）
-- ============================================================

-- 系统表（最高层级分类，如 WMS、OMS、TMS）
CREATE TABLE IF NOT EXISTS lowcode_system (
    system_id   VARCHAR(64) PRIMARY KEY,
    system_name VARCHAR(255),
    sort_order  INT DEFAULT 0,
    created_at  VARCHAR(64)
);

-- 页面元数据表
CREATE TABLE IF NOT EXISTS lowcode_page (
    page_id     VARCHAR(64) PRIMARY KEY,
    page_name   VARCHAR(255),
    table_name  VARCHAR(255),
    system_id   VARCHAR(64),
    module_id   VARCHAR(64),
    page_type   VARCHAR(32),
    schema_json TEXT NOT NULL,
    page_css    TEXT,
    enabled     BOOLEAN DEFAULT TRUE,
    deleted     BOOLEAN DEFAULT FALSE,
    deleted_at  VARCHAR(64),
    saved_at    VARCHAR(64),
    updated_at  VARCHAR(64)
);
-- 兼容已有数据库：补 system_id 列
ALTER TABLE lowcode_page ADD COLUMN IF NOT EXISTS system_id VARCHAR(64);

-- 模块树表
CREATE TABLE IF NOT EXISTS lowcode_module (
    module_id   VARCHAR(64) PRIMARY KEY,
    parent_id   VARCHAR(64),
    module_name VARCHAR(255),
    sort_order  INT DEFAULT 0,
    created_at  VARCHAR(64)
);

-- API 注册表
CREATE TABLE IF NOT EXISTS lowcode_api_registry (
    id          VARCHAR(64) PRIMARY KEY,
    url         VARCHAR(512),
    method      VARCHAR(16),
    perm_code   VARCHAR(128),
    description VARCHAR(512),
    group_name  VARCHAR(128),
    updated_at  VARCHAR(64)
);

-- 高级查询方案表
CREATE TABLE IF NOT EXISTS lowcode_search_scheme (
    id          SERIAL PRIMARY KEY,
    grid_id     VARCHAR(128) NOT NULL,
    scheme_name VARCHAR(255),
    conditions  TEXT,
    is_default  BOOLEAN DEFAULT FALSE,
    created_at  VARCHAR(64)
);
-- 页面字段/按钮权限码表（从 schema_json 中提取，独立存储便于授权查询）
CREATE TABLE IF NOT EXISTS lowcode_page_perm (
    id          SERIAL PRIMARY KEY,
    page_id     VARCHAR(64) NOT NULL,
    perm_type   VARCHAR(16) NOT NULL,
    item_key    VARCHAR(255),
    item_label  VARCHAR(255),
    perm_code   VARCHAR(128),
    sort_order  INT DEFAULT 0,
    updated_at  VARCHAR(64)
);
-- ============================================================
-- 多语言支持表
-- ============================================================

-- 语言列表
CREATE TABLE IF NOT EXISTS lowcode_lang (
    locale      VARCHAR(16) PRIMARY KEY,
    name        VARCHAR(64),
    sort_order  INT DEFAULT 0,
    enabled     BOOLEAN DEFAULT TRUE,
    is_rtl      BOOLEAN DEFAULT FALSE,
    created_at  VARCHAR(64)
);

-- 翻译文本（text_key 为中文原文，text_val 为目标语言译文）
CREATE TABLE IF NOT EXISTS lowcode_lang_text (
    id          SERIAL PRIMARY KEY,
    locale      VARCHAR(16) NOT NULL,
    text_key    VARCHAR(500) NOT NULL,
    text_val    TEXT,
    module      VARCHAR(64) DEFAULT 'common',
    updated_at  VARCHAR(64),
    UNIQUE(locale, text_key)
);

CREATE INDEX IF NOT EXISTS idx_lang_text_locale ON lowcode_lang_text(locale);
CREATE INDEX IF NOT EXISTS idx_lang_text_module ON lowcode_lang_text(locale, module);

-- 种子语言数据（ON CONFLICT 保证可重复执行）
INSERT INTO lowcode_lang (locale, name, sort_order, enabled, is_rtl, created_at) VALUES
    ('zh-CN', '简体中文', 0, TRUE, FALSE, now()::text)
ON CONFLICT (locale) DO NOTHING;
INSERT INTO lowcode_lang (locale, name, sort_order, enabled, is_rtl, created_at) VALUES
    ('en-US', 'English', 1, TRUE, FALSE, now()::text)
ON CONFLICT (locale) DO NOTHING;
INSERT INTO lowcode_lang (locale, name, sort_order, enabled, is_rtl, created_at) VALUES
    ('ja-JP', '日本語', 2, TRUE, FALSE, now()::text)
ON CONFLICT (locale) DO NOTHING;
INSERT INTO lowcode_lang (locale, name, sort_order, enabled, is_rtl, created_at) VALUES
    ('ko-KR', '한국어', 3, TRUE, FALSE, now()::text)
ON CONFLICT (locale) DO NOTHING;
INSERT INTO lowcode_lang (locale, name, sort_order, enabled, is_rtl, created_at) VALUES
    ('ar-SA', 'العربية', 4, TRUE, TRUE, now()::text)
ON CONFLICT (locale) DO NOTHING;

-- 种子翻译数据：common 模块（通用按钮/提示）
INSERT INTO lowcode_lang_text (locale, text_key, text_val, module, updated_at) VALUES
    ('en-US', '保存', 'Save', 'common', now()::text),
    ('en-US', '取消', 'Cancel', 'common', now()::text),
    ('en-US', '删除', 'Delete', 'common', now()::text),
    ('en-US', '新增', 'Add', 'common', now()::text),
    ('en-US', '编辑', 'Edit', 'common', now()::text),
    ('en-US', '确定', 'OK', 'common', now()::text),
    ('en-US', '关闭', 'Close', 'common', now()::text),
    ('en-US', '查询', 'Search', 'common', now()::text),
    ('en-US', '重置', 'Reset', 'common', now()::text),
    ('en-US', '操作', 'Action', 'common', now()::text),
    ('en-US', '确认', 'Confirm', 'common', now()::text),
    ('en-US', '是', 'Yes', 'common', now()::text),
    ('en-US', '否', 'No', 'common', now()::text),
    ('en-US', '加载中', 'Loading', 'common', now()::text),
    ('en-US', '暂无数据', 'No data', 'common', now()::text),
    ('en-US', '操作成功', 'Operation succeeded', 'common', now()::text),
    ('en-US', '操作失败', 'Operation failed', 'common', now()::text),
    ('en-US', '后端连接异常', 'Backend connection error', 'common', now()::text)
ON CONFLICT (locale, text_key) DO NOTHING;
-- ============================================================
-- 页面主题支持
-- ============================================================

-- 主题表（预置 + 自定义）
CREATE TABLE IF NOT EXISTS lowcode_theme (
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    is_preset   BOOLEAN DEFAULT FALSE,
    theme_json  TEXT NOT NULL,
    create_time VARCHAR(64),
    update_time VARCHAR(64)
);

-- 主题全局配置（如默认主题）
CREATE TABLE IF NOT EXISTS lowcode_theme_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL
);

-- 种子预置主题（5 套扁平化蓝色系，ON CONFLICT 保证可重复执行）
INSERT INTO lowcode_theme (id, name, is_preset, theme_json, create_time) VALUES
    ('theme_preset_01', '扁平蓝-浅色', TRUE, '{"colors":{"primary":"#1976d2","primaryHover":"#1565c0","success":"#52c41a","warning":"#faad14","danger":"#f5222d","info":"#909399","pageBg":"#f0f2f5","cardBg":"#ffffff","textPrimary":"#303133","textSecondary":"#606266","border":"#dcdfe6"},"radius":0}', now()::text)
ON CONFLICT (id) DO NOTHING;
INSERT INTO lowcode_theme (id, name, is_preset, theme_json, create_time) VALUES
    ('theme_preset_02', '扁平蓝-深色', TRUE, '{"colors":{"primary":"#42a5f5","primaryHover":"#64b5f6","success":"#66bb6a","warning":"#ffa726","danger":"#ef5350","info":"#78909c","pageBg":"#1e1e1e","cardBg":"#2d2d2d","textPrimary":"#e0e0e0","textSecondary":"#bdbdbd","border":"#424242"},"radius":0}', now()::text)
ON CONFLICT (id) DO NOTHING;
INSERT INTO lowcode_theme (id, name, is_preset, theme_json, create_time) VALUES
    ('theme_preset_03', '靛蓝-浅色', TRUE, '{"colors":{"primary":"#3f51b5","primaryHover":"#303f9f","success":"#4caf50","warning":"#ff9800","danger":"#f44336","info":"#9e9e9e","pageBg":"#f0f2f5","cardBg":"#ffffff","textPrimary":"#303133","textSecondary":"#606266","border":"#dcdfe6"},"radius":0}', now()::text)
ON CONFLICT (id) DO NOTHING;
INSERT INTO lowcode_theme (id, name, is_preset, theme_json, create_time) VALUES
    ('theme_preset_04', '青蓝-浅色', TRUE, '{"colors":{"primary":"#0097a7","primaryHover":"#00838f","success":"#43a047","warning":"#fb8c00","danger":"#e53935","info":"#757575","pageBg":"#f0f2f5","cardBg":"#ffffff","textPrimary":"#303133","textSecondary":"#606266","border":"#dcdfe6"},"radius":0}', now()::text)
ON CONFLICT (id) DO NOTHING;
INSERT INTO lowcode_theme (id, name, is_preset, theme_json, create_time) VALUES
    ('theme_preset_05', '极简灰-浅色', TRUE, '{"colors":{"primary":"#455a64","primaryHover":"#37474f","success":"#2e7d32","warning":"#ef6c00","danger":"#c62828","info":"#616161","pageBg":"#f5f5f5","cardBg":"#ffffff","textPrimary":"#263238","textSecondary":"#546e7a","border":"#cfd8dc"},"radius":0}', now()::text)
ON CONFLICT (id) DO NOTHING;

-- 默认主题配置
INSERT INTO lowcode_theme_config (config_key, config_value) VALUES
    ('default_theme_id', 'theme_preset_01')
ON CONFLICT (config_key) DO NOTHING;
-- 页面保存日志表（记录每次保存操作）
CREATE TABLE IF NOT EXISTS lowcode_page_save_log (
    log_id      BIGSERIAL PRIMARY KEY,
    page_id     VARCHAR(64) NOT NULL,
    page_name   VARCHAR(255),
    table_name  VARCHAR(255),
    page_type   VARCHAR(32),
    operator    VARCHAR(64),
    action      VARCHAR(32) NOT NULL DEFAULT 'save',
    table_synced BOOLEAN DEFAULT FALSE,
    table_sync_msg VARCHAR(512),
    schema_size INT,
    saved_at    VARCHAR(64) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_save_log_page_id ON lowcode_page_save_log (page_id);
CREATE INDEX IF NOT EXISTS idx_save_log_saved_at ON lowcode_page_save_log (saved_at);
-- 字段关联表（记录 dx-base-data 控件关联的基础资料页面）
CREATE TABLE IF NOT EXISTS lowcode_field_ref (
    id                BIGSERIAL PRIMARY KEY,
    source_page_id    VARCHAR(64) NOT NULL,
    source_field_name VARCHAR(128),
    target_page_id    VARCHAR(64),
    display_field     VARCHAR(128),
    value_field       VARCHAR(128),
    updated_at        VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_field_ref_source ON lowcode_field_ref (source_page_id);
CREATE INDEX IF NOT EXISTS idx_field_ref_target ON lowcode_field_ref (target_page_id);