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
-- ============================================================
-- 通用后台引擎元数据表（配置驱动 CRUD）
-- 对应设计文档第 2.1.1 节，lc_ 前缀区分通用引擎元数据与 lowcode_ 平台配置表
-- ============================================================

-- 业务元数据主表
CREATE TABLE IF NOT EXISTS lc_biz_meta (
    id                VARCHAR(64) PRIMARY KEY,
    biz_code          VARCHAR(128) NOT NULL UNIQUE,
    biz_name          VARCHAR(255),
    main_table        VARCHAR(255) NOT NULL,
    main_table_alias  VARCHAR(64),
    status_field      VARCHAR(128),
    logic_delete_field VARCHAR(128),
    primary_key_field  VARCHAR(128),
    key_strategy       VARCHAR(32) DEFAULT 'SNOWFLAKE',
    tenant_id         VARCHAR(64),
    version           INT DEFAULT 1,
    remark            TEXT,
    created_at        VARCHAR(64),
    updated_at        VARCHAR(64)
);

-- 兼容已有表：补充 key_strategy 列
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='lc_biz_meta' AND column_name='key_strategy') THEN
    ALTER TABLE lc_biz_meta ADD COLUMN key_strategy VARCHAR(32) DEFAULT 'SNOWFLAKE';
  END IF;
END $$;

-- 字段元数据
CREATE TABLE IF NOT EXISTS lc_field_meta (
    id                VARCHAR(64) PRIMARY KEY,
    biz_code          VARCHAR(128) NOT NULL,
    field_name        VARCHAR(128) NOT NULL,
    column_type       VARCHAR(64),
    data_type         VARCHAR(64),
    is_required       BOOLEAN DEFAULT FALSE,
    is_unique         BOOLEAN DEFAULT FALSE,
    is_system_field   BOOLEAN DEFAULT FALSE,
    permission_expr   VARCHAR(512),
    default_value_expr VARCHAR(512),
    validation_expr   VARCHAR(512),
    sort_no           INT DEFAULT 0,
    base_data_ref     TEXT,
    created_at        VARCHAR(64)
);

-- 兼容已有表：补充 base_data_ref 列
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='lc_field_meta' AND column_name='base_data_ref') THEN
    ALTER TABLE lc_field_meta ADD COLUMN base_data_ref TEXT;
  END IF;
END $$;

-- 动作绑定（把 action 绑到执行策略）
CREATE TABLE IF NOT EXISTS lc_action_meta (
    id                VARCHAR(64) PRIMARY KEY,
    biz_code          VARCHAR(128) NOT NULL,
    action            VARCHAR(64) NOT NULL,
    strategy_type     VARCHAR(64),
    pre_hook_plugins  TEXT,
    post_hook_plugins TEXT,
    status_transition VARCHAR(255),
    data_scope_expr   VARCHAR(512),
    enabled           BOOLEAN DEFAULT TRUE,
    ext_api_url       VARCHAR(512),
    ext_api_method    VARCHAR(16),
    ext_data_path     VARCHAR(255),
    ext_total_path    VARCHAR(255),
    created_at        VARCHAR(64)
);

-- 兼容已有表：补充外部 API 配置列
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='lc_action_meta' AND column_name='ext_api_url') THEN
    ALTER TABLE lc_action_meta ADD COLUMN ext_api_url VARCHAR(512);
    ALTER TABLE lc_action_meta ADD COLUMN ext_api_method VARCHAR(16);
    ALTER TABLE lc_action_meta ADD COLUMN ext_data_path VARCHAR(255);
    ALTER TABLE lc_action_meta ADD COLUMN ext_total_path VARCHAR(255);
  END IF;
END $$;

-- 状态机定义
CREATE TABLE IF NOT EXISTS lc_state_machine (
    id                VARCHAR(64) PRIMARY KEY,
    biz_code          VARCHAR(128) NOT NULL,
    current_state     VARCHAR(64),
    next_state        VARCHAR(64),
    event             VARCHAR(128),
    guard_expr        VARCHAR(512),
    action            VARCHAR(64),
    enabled           BOOLEAN DEFAULT TRUE,
    created_at        VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_field_meta_biz ON lc_field_meta (biz_code);
CREATE INDEX IF NOT EXISTS idx_action_meta_biz_action ON lc_action_meta (biz_code, action);
CREATE INDEX IF NOT EXISTS idx_state_machine_biz ON lc_state_machine (biz_code);

-- ============================================================
-- 示例业务初始数据：采购单（purchaseOrder）
-- ============================================================

INSERT INTO lc_biz_meta (id, biz_code, biz_name, main_table, main_table_alias, status_field, logic_delete_field, version, remark, created_at) VALUES
    ('meta_purchase_order', 'purchaseOrder', '采购单', 'po_main', 'T', 'status', 'deleted', 1, 'P0 示例业务', now()::text)
ON CONFLICT (biz_code) DO NOTHING;

-- 采购单字段元数据
INSERT INTO lc_field_meta (id, biz_code, field_name, column_type, data_type, is_required, is_unique, is_system_field, default_value_expr, validation_expr, sort_no, created_at) VALUES
    ('po_f_id', 'purchaseOrder', 'id', 'PK', 'VARCHAR', TRUE, TRUE, TRUE, NULL, NULL, 0, now()::text),
    ('po_f_no', 'purchaseOrder', 'po_no', 'BUSINESS_NO', 'VARCHAR', TRUE, TRUE, FALSE, NULL, NULL, 1, now()::text),
    ('po_f_supplier', 'purchaseOrder', 'supplier_id', 'FK', 'VARCHAR', TRUE, FALSE, FALSE, NULL, NULL, 2, now()::text),
    ('po_f_amount', 'purchaseOrder', 'amount', 'AMOUNT', 'DECIMAL', TRUE, FALSE, FALSE, NULL, 'value > 0', 3, now()::text),
    ('po_f_status', 'purchaseOrder', 'status', 'STATUS', 'VARCHAR', TRUE, FALSE, TRUE, '''DRAFT''', NULL, 4, now()::text),
    ('po_f_deleted', 'purchaseOrder', 'deleted', 'LOGIC_DELETE', 'BOOLEAN', FALSE, FALSE, TRUE, 'false', NULL, 5, now()::text),
    ('po_f_created_at', 'purchaseOrder', 'created_at', 'TIMESTAMP', 'VARCHAR', FALSE, FALSE, TRUE, 'now()', NULL, 6, now()::text),
    ('po_f_updated_at', 'purchaseOrder', 'updated_at', 'TIMESTAMP', 'VARCHAR', FALSE, FALSE, TRUE, 'now()', NULL, 7, now()::text)
ON CONFLICT (id) DO NOTHING;

-- 采购单动作绑定
INSERT INTO lc_action_meta (id, biz_code, action, strategy_type, status_transition, enabled, created_at) VALUES
    ('po_a_create', 'purchaseOrder', 'create', 'SINGLE', NULL, TRUE, now()::text),
    ('po_a_update', 'purchaseOrder', 'update', 'SINGLE', NULL, TRUE, now()::text),
    ('po_a_delete', 'purchaseOrder', 'delete', 'SINGLE', NULL, TRUE, now()::text),
    ('po_a_query', 'purchaseOrder', 'query', 'SINGLE', NULL, TRUE, now()::text),
    ('po_a_submit', 'purchaseOrder', 'submit', 'SINGLE', 'DRAFT->SUBMITTED', TRUE, now()::text),
    ('po_a_revoke', 'purchaseOrder', 'revoke', 'SINGLE', 'SUBMITTED->DRAFT', TRUE, now()::text)
ON CONFLICT (id) DO NOTHING;

-- 采购单状态机
INSERT INTO lc_state_machine (id, biz_code, current_state, next_state, event, action, enabled, created_at) VALUES
    ('po_s_submit', 'purchaseOrder', 'DRAFT', 'SUBMITTED', 'SUBMIT', 'submit', TRUE, now()::text),
    ('po_s_revoke', 'purchaseOrder', 'SUBMITTED', 'DRAFT', 'REVOKE', 'revoke', TRUE, now()::text)
ON CONFLICT (id) DO NOTHING;

-- 采购单业务表（P0 示例，实际由 BusinessTableSyncService 在表单保存时同步建表）
CREATE TABLE IF NOT EXISTS po_main (
    id          VARCHAR(64) PRIMARY KEY,
    po_no       VARCHAR(64),
    supplier_id VARCHAR(64),
    amount      DECIMAL(18,2),
    status      VARCHAR(32) DEFAULT 'DRAFT',
    deleted     BOOLEAN DEFAULT FALSE,
    created_at  VARCHAR(64),
    updated_at  VARCHAR(64)
);
-- ============================================================
-- 插件注册表（P2 插件容器）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_plugin_registry (
    id              VARCHAR(64) PRIMARY KEY,
    plugin_id       VARCHAR(128) NOT NULL,
    version         VARCHAR(64) NOT NULL,
    name            VARCHAR(255),
    entry_class     VARCHAR(512),
    jar_path        VARCHAR(512),
    status          VARCHAR(32) DEFAULT 'LOADED',
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64),
    UNIQUE(plugin_id, version)
);

CREATE INDEX IF NOT EXISTS idx_plugin_registry_id ON lc_plugin_registry (plugin_id);
-- ============================================================
-- 事件订阅与异步消费（P3 事件与异步）
-- ============================================================

-- 事件订阅配置表（配置订阅：NOTIFY/WEBHOOK/MQ_TOPIC）
CREATE TABLE IF NOT EXISTS lc_event_subscription (
    id              VARCHAR(64) PRIMARY KEY,
    biz_code        VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    handler_type    VARCHAR(64) NOT NULL,
    handler_config  TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_event_sub_biz_event ON lc_event_subscription (biz_code, event_type);

-- 事件消费幂等去重表
CREATE TABLE IF NOT EXISTS lc_event_consume_log (
    id              BIGSERIAL PRIMARY KEY,
    message_key     VARCHAR(255) NOT NULL UNIQUE,
    topic           VARCHAR(128),
    consumed_at     VARCHAR(64)
);
-- ============================================================
-- 插件权限审批（P5 安全与监控）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_plugin_permission (
    id              VARCHAR(64) PRIMARY KEY,
    plugin_id       VARCHAR(128) NOT NULL,
    permission      VARCHAR(255) NOT NULL,
    status          VARCHAR(32) DEFAULT 'PENDING',
    approved_by     VARCHAR(64),
    approved_at     VARCHAR(64),
    created_at      VARCHAR(64),
    UNIQUE(plugin_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_plugin_perm_pid ON lc_plugin_permission (plugin_id);
-- ============================================================
-- 审批流（FE-S6 对应后端）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_workflow_def (
    id              VARCHAR(64) PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(255),
    nodes           TEXT,
    transitions     TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    workflow_type   VARCHAR(32),
    bill_code       VARCHAR(128),
    description     VARCHAR(512),
    params          TEXT,
    control         TEXT,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);

ALTER TABLE lc_workflow_def ADD COLUMN workflow_type VARCHAR(32);
ALTER TABLE lc_workflow_def ADD COLUMN bill_code VARCHAR(128);
ALTER TABLE lc_workflow_def ADD COLUMN description VARCHAR(512);
ALTER TABLE lc_workflow_def ADD COLUMN params TEXT;
ALTER TABLE lc_workflow_def ADD COLUMN control TEXT;

CREATE TABLE IF NOT EXISTS lc_workflow_instance (
    id              VARCHAR(64) PRIMARY KEY,
    workflow_code   VARCHAR(128) NOT NULL,
    biz_code        VARCHAR(128) NOT NULL,
    biz_id          VARCHAR(64) NOT NULL,
    current_node    VARCHAR(128),
    status          VARCHAR(32) DEFAULT 'RUNNING',
    initiator       VARCHAR(64),
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS lc_workflow_task (
    id              VARCHAR(64) PRIMARY KEY,
    instance_id     VARCHAR(64) NOT NULL,
    biz_code        VARCHAR(128) NOT NULL,
    biz_id          VARCHAR(64) NOT NULL,
    title           VARCHAR(255),
    workflow_name   VARCHAR(255),
    current_node    VARCHAR(128),
    initiator       VARCHAR(64),
    assignee        VARCHAR(64),
    status          VARCHAR(32) DEFAULT 'PENDING',
    created_at      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS lc_approval_record (
    id              VARCHAR(64) PRIMARY KEY,
    instance_id     VARCHAR(64) NOT NULL,
    biz_code        VARCHAR(128),
    biz_id          VARCHAR(64),
    current_node    VARCHAR(128),
    action          VARCHAR(32),
    operator        VARCHAR(64),
    comment         TEXT,
    operated_at     VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_wf_inst_biz ON lc_workflow_instance (biz_code, biz_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_assignee ON lc_workflow_task (assignee, status);
CREATE INDEX IF NOT EXISTS idx_approval_inst ON lc_approval_record (instance_id);

-- 业务流增强：流程实例新增变量/等待恢复字段
ALTER TABLE lc_workflow_instance ADD COLUMN IF NOT EXISTS variables TEXT;
ALTER TABLE lc_workflow_instance ADD COLUMN IF NOT EXISTS resume_at VARCHAR(64);
ALTER TABLE lc_workflow_instance ADD COLUMN IF NOT EXISTS wait_condition VARCHAR(512);
CREATE INDEX IF NOT EXISTS idx_wf_inst_wait ON lc_workflow_instance (status, resume_at);

-- ============================================================
-- 权限体系（G3 + API 治理）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_role (
    id              VARCHAR(64) PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(255),
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS lc_permission_gateway (
    id              VARCHAR(64) PRIMARY KEY,
    biz_code        VARCHAR(128) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    role_code       VARCHAR(128) NOT NULL,
    allowed         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    UNIQUE(biz_code, action, role_code)
);

CREATE TABLE IF NOT EXISTS lc_permission_field (
    id              VARCHAR(64) PRIMARY KEY,
    biz_code        VARCHAR(128) NOT NULL,
    field_name      VARCHAR(128) NOT NULL,
    role_code       VARCHAR(128) NOT NULL,
    perm_type       VARCHAR(32) DEFAULT 'editable',
    created_at      VARCHAR(64),
    UNIQUE(biz_code, field_name, role_code)
);

CREATE INDEX IF NOT EXISTS idx_perm_gw_biz ON lc_permission_gateway (biz_code);
CREATE INDEX IF NOT EXISTS idx_perm_field_biz ON lc_permission_field (biz_code);

-- ============================================================
-- 数据字典（FE-S10 P2）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_data_dict (
    id              VARCHAR(64) PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(255),
    description     TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS lc_data_dict_item (
    id              VARCHAR(64) PRIMARY KEY,
    dict_code       VARCHAR(128) NOT NULL,
    item_value      VARCHAR(255),
    item_label      VARCHAR(255),
    sort_no         INT DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_dict_item_code ON lc_data_dict_item (dict_code);

-- ============================================================
-- 附件管理（FE-S10 P2）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_attachment (
    id              VARCHAR(64) PRIMARY KEY,
    biz_code        VARCHAR(128),
    biz_id          VARCHAR(64),
    file_name       VARCHAR(512),
    file_path       VARCHAR(1024),
    file_size       BIGINT,
    file_type       VARCHAR(128),
    uploader        VARCHAR(64),
    created_at      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_attach_biz ON lc_attachment (biz_code, biz_id);

-- ============================================================
-- 元数据版本历史（FE-S4 方向3）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_meta_version (
    id              VARCHAR(64) PRIMARY KEY,
    meta_key        VARCHAR(255) NOT NULL,
    version_no      INT NOT NULL,
    content         TEXT,
    operator        VARCHAR(64),
    remark          TEXT,
    created_at      VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_meta_version_key ON lc_meta_version (meta_key, version_no);

-- ============================================================
-- 元数据注册表（方向二：统一元数据驱动，MetaManageView 使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS lc_meta_registry (
    id              VARCHAR(64) PRIMARY KEY,
    domain          VARCHAR(64) NOT NULL,
    meta_type       VARCHAR(128) NOT NULL,
    meta_key        VARCHAR(255) NOT NULL,
    meta_name       VARCHAR(255),
    content         TEXT,
    version_no      INT DEFAULT 1,
    enabled         BOOLEAN DEFAULT TRUE,
    updated_at      VARCHAR(64),
    page_id         VARCHAR(64),
    biz_code        VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_meta_registry_domain ON lc_meta_registry (domain, meta_type);
CREATE INDEX IF NOT EXISTS idx_meta_registry_key ON lc_meta_registry (meta_key);
-- ============================================================
-- 应用包（FE-S5 方向4）
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_app_package (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    version         VARCHAR(64),
    page_ids        TEXT,
    meta_keys       TEXT,
    description     TEXT,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
-- ============================================================
-- 基础数据引擎 V3 元数据表（BaseDataEngine）
-- 原始 DDL 位于 docs/ddl-base-data-engine-v3.sql，合入此处以便 LowcodeTableInitializer 自动建表
-- ============================================================

-- 1. BASE_DATA_ENTITY — 基础数据实体
CREATE TABLE IF NOT EXISTS BASE_DATA_ENTITY (
    CACHE_NAME        VARCHAR(64)    NOT NULL,
    ENTITY_NAME       VARCHAR(128)   NOT NULL,
    TABLE_NAME        VARCHAR(128)   NOT NULL,
    ID_COLUMN         VARCHAR(64)    NOT NULL,
    CODE_COLUMN       VARCHAR(64),
    CACHE_TYPE        VARCHAR(32)    DEFAULT 'REDIS' NOT NULL,
    TTL               INTEGER        DEFAULT 86400  NOT NULL,
    MAX_CAPACITY      INTEGER        DEFAULT 100000 NOT NULL,
    ENABLED           BOOLEAN        DEFAULT TRUE  NOT NULL,
    VERSION           INTEGER        DEFAULT 1     NOT NULL,
    SCHEMA_VERSION    INTEGER        DEFAULT 1     NOT NULL,
    SCHEMA_HASH       VARCHAR(64),
    UPDATE_TIME       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_BASE_DATA_ENTITY PRIMARY KEY (CACHE_NAME)
);

-- 2. BASE_DATA_FIELD — 字段元数据
CREATE TABLE IF NOT EXISTS BASE_DATA_FIELD (
    CACHE_NAME        VARCHAR(64)    NOT NULL,
    COLUMN_NAME       VARCHAR(64)    NOT NULL,
    DATA_TYPE         VARCHAR(32)    NOT NULL,
    DATA_LENGTH       INTEGER,
    DATA_PRECISION    INTEGER,
    DATA_SCALE        INTEGER,
    NULLABLE          BOOLEAN        DEFAULT TRUE NOT NULL,
    COLUMN_INDEX      INTEGER        NOT NULL,
    ENABLED           BOOLEAN        DEFAULT TRUE NOT NULL,
    FIELD_VERSION     INTEGER        DEFAULT 1    NOT NULL,
    IS_ENCRYPTED      BOOLEAN        DEFAULT FALSE NOT NULL,
    FIELD_TTL         INTEGER,
    CONSTRAINT PK_BASE_DATA_FIELD PRIMARY KEY (CACHE_NAME, COLUMN_NAME)
);

-- 3. BASE_DATA_RELATION — 关联关系
CREATE TABLE IF NOT EXISTS BASE_DATA_RELATION (
    SOURCE_CACHE      VARCHAR(64)    NOT NULL,
    SOURCE_COLUMN     VARCHAR(64)    NOT NULL,
    TARGET_CACHE      VARCHAR(64)    NOT NULL,
    TARGET_COLUMN     VARCHAR(64)    NOT NULL,
    RELATION_TYPE     VARCHAR(32)    DEFAULT 'MANY_TO_ONE' NOT NULL,
    ENABLED           BOOLEAN        DEFAULT TRUE NOT NULL,
    CONSTRAINT PK_BASE_DATA_RELATION PRIMARY KEY (SOURCE_CACHE, SOURCE_COLUMN, TARGET_CACHE)
);

-- 4. BASE_DATA_PROJECTION — 字段投影
CREATE TABLE IF NOT EXISTS BASE_DATA_PROJECTION (
    CACHE_NAME        VARCHAR(64)    NOT NULL,
    PROJECTION_NAME   VARCHAR(32)    NOT NULL,
    FIELDS            TEXT           NOT NULL,
    ENABLED           BOOLEAN        DEFAULT TRUE NOT NULL,
    CONSTRAINT PK_BASE_DATA_PROJECTION PRIMARY KEY (CACHE_NAME, PROJECTION_NAME)
);

-- 5. BASE_DATA_CACHE — 缓存策略
CREATE TABLE IF NOT EXISTS BASE_DATA_CACHE (
    CACHE_NAME             VARCHAR(64)    NOT NULL,
    CACHE_TYPE             VARCHAR(32)    DEFAULT 'REDIS'     NOT NULL,
    TTL                    INTEGER        DEFAULT 86400        NOT NULL,
    MAX_CAPACITY           INTEGER        DEFAULT 100000       NOT NULL,
    ENABLE_LOCAL_CACHE     BOOLEAN        DEFAULT TRUE         NOT NULL,
    ENABLE_NULL_CACHE      BOOLEAN        DEFAULT TRUE         NOT NULL,
    ENABLE_PREWARM         BOOLEAN        DEFAULT FALSE        NOT NULL,
    ENABLE_PROTECTION      BOOLEAN        DEFAULT TRUE         NOT NULL,
    BATCH_SIZE             INTEGER        DEFAULT 1000         NOT NULL,
    RANDOM_TTL             BOOLEAN        DEFAULT TRUE         NOT NULL,
    MAX_RELATION_DEPTH     SMALLINT       DEFAULT 5            NOT NULL,
    NULL_TTL               INTEGER        DEFAULT 60           NOT NULL,
    LOCK_TIMEOUT_MS        INTEGER        DEFAULT 3000         NOT NULL,
    ENABLE_SWR             BOOLEAN        DEFAULT TRUE         NOT NULL,
    SWR_REFRESH_TTL        INTEGER        DEFAULT 7200         NOT NULL,
    ENABLE_CIRCUIT_BREAKER BOOLEAN        DEFAULT TRUE         NOT NULL,
    CB_FAILURE_THRESHOLD   SMALLINT       DEFAULT 10           NOT NULL,
    CB_RECOVERY_MS         INTEGER        DEFAULT 30000        NOT NULL,
    ADMISSION_THRESHOLD    SMALLINT       DEFAULT 3            NOT NULL,
    MAX_BATCH_IDS          INTEGER        DEFAULT 2000         NOT NULL,
    MAX_RESULT_ROWS        INTEGER        DEFAULT 5000         NOT NULL,
    MAX_PAYLOAD_BYTES      INTEGER        DEFAULT 5242880      NOT NULL,
    CONSISTENCY_LEVEL      VARCHAR(16)    DEFAULT 'EVENTUAL'   NOT NULL,
    DEGRADE_POLICY         VARCHAR(16)    DEFAULT 'FAIL_FAST'  NOT NULL,
    REVISION_STRATEGY      VARCHAR(16)    DEFAULT 'NONE'       NOT NULL,
    CONSTRAINT PK_BASE_DATA_CACHE PRIMARY KEY (CACHE_NAME)
);

-- 6. BASE_DATA_SCHEMA_CHANGE — Schema 变更记录
CREATE TABLE IF NOT EXISTS BASE_DATA_SCHEMA_CHANGE (
    CHANGE_ID         BIGINT        NOT NULL,
    CACHE_NAME        VARCHAR(64)   NOT NULL,
    OLD_VERSION       INTEGER,
    NEW_VERSION       INTEGER       NOT NULL,
    CHANGE_TYPE       VARCHAR(32)   NOT NULL,
    CHANGE_DETAIL     TEXT,
    SCHEMA_HASH_OLD   VARCHAR(64),
    SCHEMA_HASH_NEW   VARCHAR(64),
    CREATE_TIME       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_BASE_DATA_SCHEMA_CHANGE PRIMARY KEY (CHANGE_ID)
);

-- 7. CACHE_EVENT_OUTBOX — 事件 Outbox
CREATE TABLE IF NOT EXISTS CACHE_EVENT_OUTBOX (
    EVENT_ID          BIGINT        NOT NULL,
    EVENT_TYPE        VARCHAR(64)   NOT NULL,
    CACHE_NAME        VARCHAR(64),
    SCHEMA_VERSION    INTEGER,
    IDS               TEXT,
    STATUS            VARCHAR(16)   DEFAULT 'PENDING' NOT NULL,
    RETRY_COUNT       SMALLINT      DEFAULT 0         NOT NULL,
    MAX_RETRY         SMALLINT      DEFAULT 3         NOT NULL,
    CREATE_TIME       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATE_TIME       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_CACHE_EVENT_OUTBOX PRIMARY KEY (EVENT_ID)
);

CREATE INDEX IF NOT EXISTS IDX_OUTBOX_STATUS ON CACHE_EVENT_OUTBOX (STATUS, CREATE_TIME);

-- 8. BASE_DATA_CONFIG_AUDIT — 配置审计
CREATE TABLE IF NOT EXISTS BASE_DATA_CONFIG_AUDIT (
    AUDIT_ID          BIGINT        NOT NULL,
    CACHE_NAME        VARCHAR(64),
    OPERATION         VARCHAR(32)   NOT NULL,
    OLD_CONFIG        TEXT,
    NEW_CONFIG        TEXT,
    VERSION           INTEGER,
    PUBLISH_RESULT    VARCHAR(16),
    ERROR_MESSAGE     TEXT,
    OPERATOR          VARCHAR(64),
    CREATE_TIME       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_BASE_DATA_CONFIG_AUDIT PRIMARY KEY (AUDIT_ID)
);

-- 序列
CREATE SEQUENCE IF NOT EXISTS SEQ_SCHEMA_CHANGE START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SEQ_OUTBOX_EVENT  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SEQ_CONFIG_AUDIT  START WITH 1 INCREMENT BY 1;

-- 初始数据：标准 Projection（ON CONFLICT DO NOTHING 保证可重复执行）
INSERT INTO BASE_DATA_PROJECTION (CACHE_NAME, PROJECTION_NAME, FIELDS, ENABLED) VALUES
    ('B_ITEM', 'FULL',   '["ITEM_ID","ITEM_CODE","ITEM_NAME","SPEC","UNIT_ID","OWNER_ID","CATEGORY_ID","SUPPLIER_ID"]', TRUE),
    ('B_ITEM', 'SIMPLE', '["ITEM_ID","ITEM_CODE","ITEM_NAME"]', TRUE),
    ('B_ITEM', 'PDA',    '["ITEM_ID","ITEM_CODE","ITEM_NAME","UNIT_ID"]', TRUE),
    ('B_ITEM', 'LIST',   '["ITEM_ID","ITEM_CODE","ITEM_NAME","SPEC"]', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO BASE_DATA_PROJECTION (CACHE_NAME, PROJECTION_NAME, FIELDS, ENABLED) VALUES
    ('B_OWNER', 'FULL',   '["OWNER_ID","OWNER_CODE","OWNER_NAME"]', TRUE),
    ('B_OWNER', 'SIMPLE', '["OWNER_ID","OWNER_CODE","OWNER_NAME"]', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO BASE_DATA_PROJECTION (CACHE_NAME, PROJECTION_NAME, FIELDS, ENABLED) VALUES
    ('B_WAREHOUSE', 'FULL',   '["WAREHOUSE_ID","WAREHOUSE_CODE","WAREHOUSE_NAME"]', TRUE),
    ('B_WAREHOUSE', 'SIMPLE', '["WAREHOUSE_ID","WAREHOUSE_CODE","WAREHOUSE_NAME"]', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO BASE_DATA_PROJECTION (CACHE_NAME, PROJECTION_NAME, FIELDS, ENABLED) VALUES
    ('B_UNIT', 'FULL',   '["UNIT_ID","UNIT_CODE","UNIT_NAME"]', TRUE),
    ('B_UNIT', 'SIMPLE', '["UNIT_ID","UNIT_CODE","UNIT_NAME"]', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO BASE_DATA_PROJECTION (CACHE_NAME, PROJECTION_NAME, FIELDS, ENABLED) VALUES
    ('B_CATEGORY', 'FULL',   '["CATEGORY_ID","CATEGORY_CODE","CATEGORY_NAME"]', TRUE),
    ('B_CATEGORY', 'SIMPLE', '["CATEGORY_ID","CATEGORY_CODE","CATEGORY_NAME"]', TRUE)
ON CONFLICT DO NOTHING;

-- 初始缓存策略
INSERT INTO BASE_DATA_CACHE (CACHE_NAME, CACHE_TYPE, TTL, MAX_CAPACITY, ENABLE_LOCAL_CACHE, ENABLE_NULL_CACHE, ENABLE_PREWARM, ENABLE_PROTECTION, BATCH_SIZE, RANDOM_TTL, MAX_RELATION_DEPTH, NULL_TTL, LOCK_TIMEOUT_MS, ENABLE_SWR, SWR_REFRESH_TTL, ENABLE_CIRCUIT_BREAKER, CB_FAILURE_THRESHOLD, CB_RECOVERY_MS, ADMISSION_THRESHOLD, MAX_BATCH_IDS, MAX_RESULT_ROWS, MAX_PAYLOAD_BYTES, CONSISTENCY_LEVEL, DEGRADE_POLICY, REVISION_STRATEGY) VALUES
    ('B_ITEM', 'REDIS', 3600, 100000, TRUE, TRUE, FALSE, TRUE, 1000, TRUE, 5, 60, 3000, TRUE, 7200, TRUE, 10, 30000, 3, 2000, 5000, 5242880, 'EVENTUAL', 'FAIL_FAST', 'NONE'),
    ('B_OWNER', 'REDIS', 7200, 20000, TRUE, TRUE, TRUE, TRUE, 1000, TRUE, 3, 60, 3000, TRUE, 7200, TRUE, 10, 30000, 2, 2000, 5000, 5242880, 'EVENTUAL', 'FAIL_FAST', 'NONE'),
    ('B_WAREHOUSE', 'REDIS', 7200, 10000, TRUE, TRUE, TRUE, TRUE, 1000, TRUE, 3, 60, 3000, TRUE, 7200, TRUE, 10, 30000, 2, 2000, 5000, 5242880, 'EVENTUAL', 'FAIL_FAST', 'NONE'),
    ('B_UNIT', 'REDIS', 7200, 10000, TRUE, TRUE, TRUE, TRUE, 1000, TRUE, 2, 60, 3000, TRUE, 7200, TRUE, 10, 30000, 2, 2000, 5000, 5242880, 'EVENTUAL', 'FAIL_FAST', 'NONE'),
    ('B_CATEGORY', 'REDIS', 7200, 10000, TRUE, TRUE, TRUE, TRUE, 1000, TRUE, 3, 60, 3000, TRUE, 7200, TRUE, 10, 30000, 2, 2000, 5000, 5242880, 'EVENTUAL', 'FAIL_FAST', 'NONE')
ON CONFLICT DO NOTHING;