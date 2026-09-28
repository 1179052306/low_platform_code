# 低代码平台完整改造设计文档

> 版本：v4.0（最终版）  
> 日期：2026-09-22  
> 模块：bcd-code（端口 3015）  
> 数据源：lowcode（元数据库）/ project（业务库）  
> 整合：元数据驱动 API + 状态触发动作 + 审批流 + 三层扩展体系（表达式/脚本/插件）  
> v4.0 变更：同步动作事务化（compileXxx + executeInTransaction）+ 微服务插件分发（MinIO+Nacos）

---

## 一、改造目标与原则

### 1.1 目标

1. **元数据驱动 CRUD**：设计器配置数据模型 + API 元数据 → 自动生成对外接口，零代码
2. **状态变更触发动作**：单据状态变更 → 配置化触发单据转换/外部接口/审批/自定义逻辑
3. **审批流从零搭建**：线性审批 MVP，审批通过/拒绝自动流转状态
4. **三层扩展体系**：交付后不改平台代码，通过表达式/脚本/插件扩展
5. **全部复用现有基础设施**：SqlEngineFacade + 三级缓存 + RocketMQ + BatchQueryStrategy

### 1.2 核心原则

| 原则           | 说明                                                                                |
| -------------- | ----------------------------------------------------------------------------------- |
| 平台代码不修改 | 交付后所有自定义逻辑通过配置/脚本/插件实现                                          |
| 元数据驱动     | 表名/字段/数据源/操作类型/字段映射全部来自元数据                                    |
| 复用不造轮子   | 缓存复用 L1CaffeineCache+L2RedisCache，查询复用 BatchQueryStrategy，不 new Caffeine |
| 同步/异步分离  | 影响一致性的同步（事务内），纯通知的异步（RocketMQ）                                |
| 安全防护       | 字段白名单、conditions 非空、sqlTemplate 黑名单、批量上限、审计日志                 |

---

## 二、现状分析

### 2.1 后端现状

| 项       | 现状                                                                                                                                      |
| -------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 包结构   | `com.api.lowcode.*`，每模块 Controller+Service，无 Entity/Mapper                                                                          |
| 数据访问 | `LowcodeHelper`（封装 `SqlEngineFacade`，dbName 硬编码 `"lowcode"`）                                                                      |
| SQL 引擎 | `SqlEngineFacade`：query/insert/update/delete/batchInsert/count，全参数化，`SqlFieldValidator` 白名单校验，`SqlTemplateEngine` 模板占位符 |
| 统一返回 | `ResMsg`（res/code/msg/errtype/data/customValue），无静态工厂                                                                             |
| 接口风格 | 统一 POST + `@RequestBody JSONObject` + ResMsg 包装                                                                                       |
| 事务     | 无 `@Transactional`，用 `SqlEngineFacade.executeInTransaction`                                                                            |
| 主类扫描 | `@ComponentScan(basePackages = {"com.server", "com.api"})`，覆盖 com.api.\* 全部子包                                                      |

### 2.2 现有缓存设施（全部复用）

| 设施                        | 路径                                                  | 复用于                           |
| --------------------------- | ----------------------------------------------------- | -------------------------------- |
| `L1CaffeineCache`           | `com.server.basedata.cache.L1CaffeineCache`           | 元数据缓存 + code→id L1          |
| `L2RedisCache`              | `com.server.basedata.cache.L2RedisCache`              | code→id L2（Redisson 管道化）    |
| `KeyBuilder`                | `com.server.basedata.cache.KeyBuilder`                | 缓存 key 构造                    |
| `BatchQueryStrategy`        | `com.server.basedata.database.BatchQueryStrategy`     | 批量 IN 查询回源（默认 1000/批） |
| `CacheInvalidationListener` | `com.server.basedata.event.CacheInvalidationListener` | RocketMQ 跨实例失效广播          |
| `SqlFieldValidator`         | `com.server.sqlengine.validator.SqlFieldValidator`    | 表列结构校验（5min TTL）         |

### 2.3 现有表

| 表                                   | 用途                         |
| ------------------------------------ | ---------------------------- |
| `lowcode_page`                       | 页面元数据（schema_json）    |
| `lowcode_api_registry`               | API 登记簿（6 字段，需扩展） |
| `lowcode_lang` / `lowcode_lang_text` | 多语言                       |

---

## 三、整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│  外部消费者                                                        │
└──────────────────────────────┬───────────────────────────────────┘
                               │ POST /api/open/**
┌──────────────────────────────▼───────────────────────────────────┐
│  OpenApiController                                               │
│  Token 校验 → 提取 path → 查 API 元数据(缓存) → access log        │
└──────────────────────────────┬───────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────┐
│  MetaEngineService（元数据驱动执行引擎）                            │
│                                                                   │
│  ① FieldGuard          → 字段白名单过滤（防越权）                    │
│  ② FieldTransformer     → 单条：字段重命名 + code→id 转换           │
│  ③ BatchFieldTransformer→ 批量：批量预查询 + 内存映射               │
│  ④ conditions 校验      → delete/update 强制非空                    │
│  ⑤ SqlEngineFacade      → 编译参数化 SQL → 执行                     │
│  ⑥ StatusChangeDetector → 检测状态变更 → 触发动作                   │
└──────────────────────────────┬───────────────────────────────────┘
                               │ 状态变更
┌──────────────────────────────▼───────────────────────────────────┐
│  TriggerDispatcher（触发分发器）                                    │
│                                                                   │
│  sync（事务内）                    async（RocketMQ）                 │
│  ├── DocConvertAction              ├── HttpCallAction              │
│  ├── ApprovalAction                ├── MqSendAction                │
│  ├── UpdateFieldAction             ├── NotifyAction                │
│  ├── ScriptAction (Groovy)         └── ScriptAction (Groovy)       │
│  └── PluginAction (JAR插件)                                       │
└──────────────────────────────────────────────────────────────────┘

扩展体系（三层）：
  表达式（Aviator）── 字段计算、条件判断    ── 前端输入框
  脚本（Groovy）   ── 中等复杂逻辑          ── 前端代码编辑器
  插件（PF4J JAR） ── 重量级业务扩展        ── 独立项目打JAR热加载
```

---

## 四、数据库设计（DDL）

全部追加到 `bcd-code/src/main/resources/db/lowcode_tables.sql`：

### 4.1 数据模型元数据表

```sql
CREATE TABLE IF NOT EXISTS lowcode_model (
    model_code      VARCHAR(128) PRIMARY KEY,
    model_name      VARCHAR(255),
    table_name      VARCHAR(255) NOT NULL,
    db_name         VARCHAR(64) DEFAULT 'project',
    fields_json     TEXT,
    relations_json  TEXT,
    cache_enabled   BOOLEAN DEFAULT FALSE,
    cache_name      VARCHAR(128),
    code_column     VARCHAR(128),
    id_column       VARCHAR(128),
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_lowcode_model_table ON lowcode_model(table_name);
```

`fields_json` 示例：

```json
[
  {
    "name": "order_id",
    "type": "string",
    "length": 64,
    "nullable": false,
    "primaryKey": true,
    "comment": "单据ID"
  },
  {
    "name": "warehouse_id",
    "type": "string",
    "length": 64,
    "comment": "仓库ID",
    "ref": {
      "model": "wms_warehouse",
      "idColumn": "warehouse_id",
      "codeColumn": "warehouse_code"
    }
  },
  { "name": "status", "type": "string", "length": 16, "comment": "状态" }
]
```

### 4.2 扩展 API 注册表

```sql
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS api_code       VARCHAR(128);
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS model_code      VARCHAR(128);
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS operation       VARCHAR(32);
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS param_mapping   TEXT;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS sql_template    TEXT;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS validate_rules  TEXT;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS request_schema  TEXT;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS response_schema TEXT;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS db_name         VARCHAR(64);
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS enabled         BOOLEAN DEFAULT TRUE;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS version         INT DEFAULT 1;
ALTER TABLE lowcode_api_registry ADD COLUMN IF NOT EXISTS max_batch_size  INT DEFAULT 1000;
CREATE INDEX IF NOT EXISTS idx_api_registry_url ON lowcode_api_registry(url, method);
CREATE INDEX IF NOT EXISTS idx_api_registry_code ON lowcode_api_registry(api_code);
```

### 4.3 状态触发配置表

```sql
CREATE TABLE IF NOT EXISTS lowcode_status_trigger (
    trigger_id      VARCHAR(64) PRIMARY KEY,
    model_code      VARCHAR(128) NOT NULL,
    trigger_name    VARCHAR(255),
    from_status     VARCHAR(64),
    to_status       VARCHAR(64),
    status_field    VARCHAR(128) DEFAULT 'status',
    actions_json    TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_trigger_model_status
    ON lowcode_status_trigger(model_code, from_status, to_status);
```

### 4.4 审批流表（4 张表，从零搭建）

```sql
CREATE TABLE IF NOT EXISTS lowcode_approval_flow (
    flow_code       VARCHAR(64) PRIMARY KEY,
    flow_name       VARCHAR(255),
    model_code      VARCHAR(128),
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS lowcode_approval_node (
    node_id         VARCHAR(64) PRIMARY KEY,
    flow_code       VARCHAR(64) NOT NULL,
    node_name       VARCHAR(255),
    node_order      INT NOT NULL,
    approver_type   VARCHAR(32),
    approver_value  VARCHAR(512),
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_node_flow ON lowcode_approval_node(flow_code, node_order);

CREATE TABLE IF NOT EXISTS lowcode_approval_instance (
    instance_id     VARCHAR(64) PRIMARY KEY,
    flow_code       VARCHAR(64) NOT NULL,
    model_code      VARCHAR(128) NOT NULL,
    biz_id          VARCHAR(64) NOT NULL,
    current_node_id VARCHAR(64),
    status          VARCHAR(32) DEFAULT 'pending',
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_instance_biz ON lowcode_approval_instance(model_code, biz_id);

CREATE TABLE IF NOT EXISTS lowcode_approval_record (
    record_id       VARCHAR(64) PRIMARY KEY,
    instance_id     VARCHAR(64) NOT NULL,
    node_id         VARCHAR(64) NOT NULL,
    approver        VARCHAR(64) NOT NULL,
    action          VARCHAR(32) NOT NULL,
    comment         TEXT,
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_record_instance ON lowcode_approval_record(instance_id);
```

### 4.5 触发日志表 + 插件表

```sql
CREATE TABLE IF NOT EXISTS lowcode_trigger_log (
    log_id          VARCHAR(64) PRIMARY KEY,
    trigger_id      VARCHAR(64),
    model_code      VARCHAR(128),
    biz_id          VARCHAR(64),
    from_status     VARCHAR(64),
    to_status       VARCHAR(64),
    action_type     VARCHAR(32),
    action_name     VARCHAR(255),
    exec_mode       VARCHAR(32),
    status          VARCHAR(32),
    error_msg       TEXT,
    cost_ms         INT,
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_trigger_log_biz ON lowcode_trigger_log(model_code, biz_id);

CREATE TABLE IF NOT EXISTS lowcode_plugin (
    plugin_id       VARCHAR(64) PRIMARY KEY,
    plugin_name     VARCHAR(255),
    version         VARCHAR(32),
    jar_file        VARCHAR(512),
    action_types    VARCHAR(512),
    enabled         BOOLEAN DEFAULT TRUE,
    loaded          BOOLEAN DEFAULT FALSE,
    installed_at    VARCHAR(64)
);
```

---

## 五、三层扩展体系

### 5.1 扩展点接口（平台定义，插件/脚本实现）

```java
// ===== plugin-api JAR 里的接口定义 =====

// 插件访问平台能力的唯一入口（不依赖 Spring，纯接口）
public interface PlatformApi {
    JSONArray query(JSONObject req, String dbName) throws Exception;
    int insert(JSONObject req, String dbName) throws Exception;
    int update(JSONObject req, String dbName) throws Exception;
    int delete(JSONObject req, String dbName) throws Exception;
    String redisGet(String key);
    void redisSet(String key, String value, long ttlSec);
    void sendMq(String topic, Object message);
    JSONObject getModel(String modelCode) throws Exception;
    Object executeApi(String apiCode, JSONObject body) throws Exception;
}

// 动作执行器扩展点
public interface ActionExecutor {
    String getType();
    Object execute(JSONObject config, ActionContext ctx, PlatformApi api) throws Exception;
}

// 动作上下文
public class ActionContext {
    private String modelCode;
    private JSONObject oldData;
    private JSONObject newData;
    private String fromStatus;
    private String toStatus;
    private String dbName;
    private String bizId;
    private List<String> triggerChain;  // 防环
    private Map<String, Object> variables;  // 动作间传递变量
}
```

### 5.2 三层对比

| 层级   | 引擎     | 适用               | 前端形态          | 谁来写        |
| ------ | -------- | ------------------ | ----------------- | ------------- |
| 表达式 | Aviator  | 字段计算、条件判断 | 表达式输入框      | 配置人员      |
| 脚本   | Groovy   | 中等复杂逻辑       | Monaco 代码编辑器 | 配置人员/开发 |
| 插件   | PF4J JAR | 重量级业务扩展     | 上传 JAR 管理页   | 二次开发者    |

### 5.3 表达式引擎（Aviator）

前端配置：

```json
{ "field": "totalAmount", "expression": "qty * unitPrice" }
{ "condition": "status == 'draft' && totalAmount > 1000" }
```

后端执行：

```java
Expression exp = AviatorEvaluator.compile("qty * unitPrice");
Object result = exp.execute(env);  // env = 字段值 Map
```

### 5.4 脚本引擎（Groovy）

前端配置（存元数据 actions_json）：

```json
{
  "actionType": "script",
  "config": {
    "lang": "groovy",
    "script": "def stocks = facade.query(req, ctx.dbName)\nfor (s in stocks) { ... }\nreturn result"
  }
}
```

ScriptActionExecutor：

```java
@Component
public class ScriptActionExecutor implements ActionExecutor {
    @Resource private PlatformApiImpl platformApi;
    private final ConcurrentHashMap<String, Class<?>> scriptCache = new ConcurrentHashMap<>();

    @Override
    public String getType() { return "script"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx, PlatformApi api) throws Exception {
        String script = config.getString("script");
        String cacheKey = DigestUtils.md5Hex(script);

        Class<?> scriptClass = scriptCache.get(cacheKey);
        if (scriptClass == null) {
            GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader());
            scriptClass = gcl.parseClass(script);
            scriptCache.put(cacheKey, scriptClass);
        }

        Script instance = (Script) scriptClass.newInstance();
        Binding binding = new Binding();
        binding.setVariable("ctx", ctx);
        binding.setVariable("api", api);
        binding.setVariable("JSON", JSON.class);
        instance.setBinding(binding);
        return instance.run();
    }
}
```

### 5.5 插件机制（PF4J）

二次开发者写独立 Maven 项目：

```java
// my-plugin/pom.xml 依赖 plugin-api JAR（provided scope）
public class StockAdjustExecutor implements ActionExecutor {
    @Override
    public String getType() { return "stockAdjust"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx, PlatformApi api) throws Exception {
        JSONArray stocks = api.query(req, ctx.getDbName());
        for (int i = 0; i < stocks.size(); i++) {
            api.update(buildUpdateReq(stocks.getJSONObject(i)), ctx.getDbName());
        }
        api.sendMq("stock-adjusted", ctx.getBizId());
        return null;
    }
}
```

插件管理（热加载）：

```java
@Service
public class PluginManagerService {
    private final Map<String, PluginWrapper> plugins = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadPlugins() {
        File dir = new File("plugins/");
        if (dir.exists()) {
            for (File jar : dir.listFiles((f) -> f.getName().endsWith(".jar"))) {
                loadPlugin(jar);
            }
        }
    }

    public void loadPlugin(File jarFile) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{jarFile.toURI().toURL()},
            getClass().getClassLoader());
        ServiceLoader<ActionExecutor> loaders = ServiceLoader.load(ActionExecutor.class, cl);
        for (ActionExecutor executor : loaders) {
            registerExecutor(executor.getType(), executor);
        }
        plugins.put(jarFile.getName(), new PluginWrapper(jarFile, cl));
    }

    public void unloadPlugin(String jarName) throws Exception {
        PluginWrapper pw = plugins.remove(jarName);
        if (pw != null) pw.getClassLoader().close();
    }
}
```

插件通过 Java SPI（`META-INF/services/...ActionExecutor`）自动发现，打 JAR 放 `plugins/` 目录热加载，不重启。

---

## 六、核心类设计

### 6.1 文件清单

```
bcd-code/src/main/java/com/api/lowcode/
├── engine/                          -- 元数据驱动引擎
│   ├── MetaEngineService.java
│   ├── OpenApiController.java
│   ├── FieldGuard.java
│   ├── FieldTransformer.java
│   ├── BatchFieldTransformer.java
│   ├── SqlTemplateGuard.java
│   ├── MetaCacheService.java
│   ├── RefDataCacheService.java
│   └── PlatformApiImpl.java         -- PlatformApi 实现
├── model/
│   ├── ModelController.java
│   └── ModelService.java
├── trigger/                         -- 状态触发
│   ├── TriggerDispatcher.java
│   ├── StatusChangeDetector.java
│   ├── TriggerConfigService.java
│   ├── TriggerEventConsumer.java
│   ├── TriggerLogService.java
│   ├── TriggerController.java
│   └── action/
│       ├── ActionExecutor.java          -- 接口
│       ├── ActionContext.java
│       ├── DocConvertActionExecutor.java
│       ├── HttpCallActionExecutor.java
│       ├── ApprovalActionExecutor.java
│       ├── ScriptActionExecutor.java    -- Groovy 脚本
│       ├── MqSendActionExecutor.java
│       ├── UpdateFieldActionExecutor.java
│       └── NotifyActionExecutor.java
├── approval/                        -- 审批流
│   ├── ApprovalController.java
│   ├── ApprovalService.java
│   ├── ApprovalFlowService.java
│   └── ApproverResolver.java
└── plugin/                          -- 插件管理
    ├── PluginManagerService.java
    └── PluginController.java
```

### 6.2 MetaEngineService（核心执行引擎）

```java
@Service
public class MetaEngineService {
    @Resource private MetaCacheService metaCache;
    @Resource private FieldGuard fieldGuard;
    @Resource private FieldTransformer fieldTransformer;
    @Resource private BatchFieldTransformer batchFieldTransformer;
    @Resource private SqlTemplateGuard sqlTemplateGuard;
    @Resource private SqlEngineFacade facade;
    @Resource private StatusChangeDetector statusChangeDetector;

    public Object execute(JSONObject apiMeta, JSONObject body) throws Exception {
        String operation = apiMeta.getString("operation");
        String dbName = resolveDbName(apiMeta);
        JSONObject model = metaCache.getModel(apiMeta.getString("modelCode"));
        JSONArray fields = JSON.parseArray(model.getString("fieldsJson"));
        JSONObject paramMapping = JSON.parseObject(apiMeta.getString("paramMapping"));

        switch (operation) {
            case "query":      return doQuery(apiMeta, body, dbName, fields);
            case "pageQuery":  return doPageQuery(apiMeta, body, dbName, fields);
            case "insert":     return doInsert(apiMeta, body, dbName, fields, paramMapping);
            case "batchInsert":return doBatchInsert(apiMeta, body, dbName, fields, paramMapping);
            case "update":     return doUpdate(apiMeta, body, dbName, fields, paramMapping);
            case "delete":     return doDelete(apiMeta, body, dbName, fields);
            case "custom":     return doCustom(apiMeta, body, dbName, fields);
            default: throw new IllegalArgumentException("不支持的操作: " + operation);
        }
    }

    // update：执行后检测状态变更
    private int doUpdate(JSONObject api, JSONObject body, String dbName,
                          JSONArray fields, JSONObject paramMapping) throws Exception {
        JSONArray conditions = body.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("update conditions 不能为空，禁止全表更新");
        }
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        String tableName = model.getString("tableName");
        String modelCode = api.getString("modelCode");

        JSONObject data = body.getJSONObject("data");
        data = fieldGuard.filterData(data, fields);
        data = fieldTransformer.transform(data, paramMapping, dbName);

        JSONObject req = new JSONObject();
        req.put("tableName", tableName);
        req.put("data", data);
        req.put("conditions", conditions);
        int result = facade.update(req, dbName);

        // 状态变更检测
        String statusField = api.containsKey("statusField") ? api.getString("statusField") : "status";
        statusChangeDetector.detectAndFire(modelCode, tableName, statusField,
            conditions, data, dbName, new ArrayList<>());

        return result;
    }

    // batchInsert：批量上限 + 批量转换
    private int[] doBatchInsert(JSONObject api, JSONObject body, String dbName,
                                 JSONArray fields, JSONObject paramMapping) throws Exception {
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONArray rows = body.getJSONArray("data");
        int maxBatch = api.getIntValue("maxBatchSize", 1000);
        if (rows.size() > maxBatch) {
            throw new IllegalArgumentException("批量超过上限: " + rows.size() + " > " + maxBatch);
        }
        fieldGuard.filterBatch(rows, fields);
        batchFieldTransformer.transformBatch(rows, paramMapping, dbName);
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("data", rows);
        return facade.batchInsert(req, dbName);
    }

    // delete：conditions 强制非空
    private int doDelete(JSONObject api, JSONObject body, String dbName, JSONArray fields) throws Exception {
        JSONArray conditions = body.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("delete conditions 不能为空，禁止全表删除");
        }
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("conditions", conditions);
        return facade.delete(req, dbName);
    }
}
```

### 6.3 TriggerDispatcher（触发分发）

```java
@Service
public class TriggerDispatcher {
    @Resource private Map<String, ActionExecutor> executors;  // Spring 自动注入
    @Resource private PluginManagerService pluginManager;      // 插件提供的 executor
    @Resource private TriggerLogService logService;
    @Resource private RocketMQTemplate mqTemplate;
    @Resource private PlatformApiImpl platformApi;

    public void dispatch(JSONObject trigger, ActionContext ctx) {
        JSONArray actions = JSON.parseArray(trigger.getString("actionsJson"));
        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String type = action.getString("actionType");
            String execMode = action.getString("execMode");

            // 优先查插件提供的 executor
            ActionExecutor executor = executors.get(type);
            if (executor == null) executor = pluginManager.getExecutor(type);
            if (executor == null) {
                logService.fail(action, ctx, new Exception("未知动作类型: " + type), 0);
                continue;
            }

            if ("async".equals(execMode)) {
                sendAsync(action, ctx);
            } else {
                execSync(action, executor, ctx);
            }
        }
    }

    private void execSync(JSONObject action, ActionExecutor executor, ActionContext ctx) {
        long start = System.currentTimeMillis();
        try {
            executor.execute(action.getJSONObject("config"), ctx, platformApi);
            logService.success(action, ctx, System.currentTimeMillis() - start);
        } catch (Exception e) {
            logService.fail(action, ctx, e, System.currentTimeMillis() - start);
            if ("rollback".equals(action.getString("failStrategy"))) {
                rollbackStatus(ctx);
                throw new RuntimeException("动作[" + action.getString("name") + "]失败，已回滚", e);
            }
        }
    }

    private void sendAsync(JSONObject action, ActionContext ctx) {
        JSONObject msg = new JSONObject();
        msg.put("action", action);
        msg.put("ctx", JSON.toJSONString(ctx));
        mqTemplate.asyncSend("lowcode-trigger-event", MessageBuilder.withPayload(msg).build(), ...);
    }
}
```

### 6.4 缓存设计（全部复用现有设施）

#### MetaCacheService（元数据缓存，复用 L1CaffeineCache）

```java
@Service
public class MetaCacheService {
    @Resource private LowcodeHelper helper;
    @Resource private L1CaffeineCache metaL1Cache;

    public JSONObject getModel(String modelCode) throws Exception {
        String key = "meta:model:" + modelCode;
        JSONObject cached = (JSONObject) metaL1Cache.get(key);
        if (cached != null) return cached;
        JSONArray rows = helper.queryAndMap("lowcode_model", ALL_COLS,
            helper.eq("model_code", modelCode), null, false, MODEL_MAPPING);
        if (rows.isEmpty()) throw new IllegalArgumentException("模型不存在: " + modelCode);
        metaL1Cache.put(key, rows.getJSONObject(0));
        return rows.getJSONObject(0);
    }

    public JSONObject getApi(String url, String method) throws Exception { ... }
    public void evictModel(String modelCode) { metaL1Cache.invalidate("meta:model:" + modelCode); }
    public void evictApi(String url, String method) { ... }
}
```

#### RefDataCacheService（code→id 缓存，复用 L1+L2+BatchQueryStrategy）

```java
@Service
public class RefDataCacheService {
    @Resource private L1CaffeineCache refL1Cache;
    @Resource private L2RedisCache l2RedisCache;
    @Resource private BatchQueryStrategy batchQueryStrategy;
    @Resource private MetaCacheService metaCache;

    // 批量 code→id（核心：一次 IN 查询，不走逐条）
    public Map<String, String> batchCodeToId(String refModelCode, Set<String> codes, String dbName) throws Exception {
        JSONObject model = metaCache.getModel(refModelCode);
        String cacheName = model.getString("cacheName");
        String codeColumn = model.getString("codeColumn");
        String idColumn = model.getString("idColumn");

        Map<String, String> result = new HashMap<>();
        // L1 → L2 → DB（复用 BatchQueryStrategy IN 分批查询）
        // ... 详见 meta-driven-api-design.md
        return result;
    }
}
```

### 6.5 PlatformApiImpl（平台能力暴露给插件/脚本）

```java
@Service
public class PlatformApiImpl implements PlatformApi {
    @Resource private SqlEngineFacade facade;
    @Resource private RedisService redis;
    @Resource private RocketMQTemplate mqTemplate;
    @Resource private MetaCacheService metaCache;
    @Resource private MetaEngineService engine;

    @Override
    public JSONArray query(JSONObject req, String dbName) throws Exception { return facade.query(req, dbName); }
    @Override
    public int insert(JSONObject req, String dbName) throws Exception { return facade.insert(req, dbName); }
    @Override
    public int update(JSONObject req, String dbName) throws Exception { return facade.update(req, dbName); }
    @Override
    public int delete(JSONObject req, String dbName) throws Exception { return facade.delete(req, dbName); }
    @Override
    public String redisGet(String key) { return redis.getCacheObject(key); }
    @Override
    public void redisSet(String key, String value, long ttlSec) { redis.setCacheObject(key, value, ttlSec, TimeUnit.SECONDS); }
    @Override
    public void sendMq(String topic, Object message) { mqTemplate.convertAndSend(topic, message); }
    @Override
    public JSONObject getModel(String modelCode) throws Exception { return metaCache.getModel(modelCode); }
    @Override
    public Object executeApi(String apiCode, JSONObject body) throws Exception { ... }
}
```

---

## 七、接口定义

### 7.1 对外动态 API（/api/open/\*\*）

| 调用     | 示例                                                                             |
| -------- | -------------------------------------------------------------------------------- |
| 单条新增 | `POST /api/open/inbound/order/save` body=`{data:{orderNo,warehouseCode,status}}` |
| 批量新增 | `POST /api/open/inbound/order/batch-save` body=`{data:[...1000条]}`              |
| 分页查询 | `POST /api/open/inbound/order/list` body=`{conditions,page,pageSize}`            |
| 修改     | `POST /api/open/inbound/order/update` body=`{data,conditions}`                   |
| 删除     | `POST /api/open/inbound/order/delete` body=`{conditions}`                        |

### 7.2 设计器管理接口（/api/lowcode/\*）

| 接口                            | 说明                                  |
| ------------------------------- | ------------------------------------- |
| `/api/lowcode/model/*`          | 数据模型 CRUD                         |
| `/api/lowcode/api-registry/*`   | API 元数据 CRUD + test + openapi 文档 |
| `/api/lowcode/trigger/*`        | 触发配置 CRUD + test                  |
| `/api/lowcode/approval/flow/*`  | 审批流定义 CRUD                       |
| `/api/lowcode/approval/pending` | 待审批列表                            |
| `/api/lowcode/approval/approve` | 审批通过                              |
| `/api/lowcode/approval/reject`  | 审批拒绝                              |
| `/api/lowcode/approval/history` | 审批历史                              |
| `/api/lowcode/plugin/*`         | 插件管理（上传/启停/卸载）            |

---

## 八、多角色评审

| 角色     | 结论 | 关键关注                                                                                         |
| -------- | ---- | ------------------------------------------------------------------------------------------------ |
| 产品经理 | ✅   | 三层扩展覆盖从配置人员到二次开发者；审批 MVP 线性，会签后续；调试器必须做                        |
| 开发经理 | ✅   | 复用 SqlEngineFacade+三级缓存+BatchQueryStrategy+RocketMQ；PlatformApi 解耦插件；Groovy 编译缓存 |
| 安全经理 | ⚠️   | 字段白名单+conditions非空+sqlTemplate黑名单+批量上限+Token+审计日志；脚本/插件先不限制但全量审计 |
| 架构师   | ✅   | 元数据与执行解耦；同步/异步分离；ClassLoader 隔离插件；PlatformApi 稳定契约                      |
| DBA      | ✅   | DDL IF NOT EXISTS；基础数据 code 列需唯一索引；触发日志按月清理                                  |
| 运维     | ✅   | 热加载不重启；死信队列监控；Caffeine recordStats 暴露命中率                                      |

---

## 九、落地步骤

| 阶段 | 内容                                                       | 文件                          |
| ---- | ---------------------------------------------------------- | ----------------------------- |
| 1    | DDL：全部表 + ALTER                                        | `lowcode_tables.sql`          |
| 2    | 配置：application.yml + 依赖引入                           | `application.yml` / `pom.xml` |
| 3    | PlatformApi + 缓存服务                                     | `engine/` 新增 3 文件         |
| 4    | 安全守卫：FieldGuard + SqlTemplateGuard                    | `engine/` 新增 2 文件         |
| 5    | 模型管理 + API 元数据扩展                                  | `model/` + `registry/`        |
| 6    | 执行引擎 + 字段转换                                        | `engine/` 新增 4 文件         |
| 7    | 对外入口 OpenApiController                                 | `engine/`                     |
| 8    | 动作接口 + 8 种执行器                                      | `trigger/action/`             |
| 9    | 触发核心 + 异步消费者                                      | `trigger/`                    |
| 10   | MetaEngineService 嵌入状态检测                             | `engine/` 改                  |
| 11   | 审批流                                                     | `approval/` 新增 4 文件       |
| 12   | 插件管理                                                   | `plugin/` 新增 2 文件         |
| 13   | 前端：模型设计+API设计+触发配置+审批流设计+插件管理+调试器 | `web-code`                    |
| 14   | 验证：全链路联调                                           | -                             |

---

## 十、需引入的依赖（需确认）

| 依赖                             | 版本   | 大小   | 用途                                     |
| -------------------------------- | ------ | ------ | ---------------------------------------- |
| `org.pf4j:pf4j`                  | 3.9.0  | ~200KB | 插件框架（热加载/卸载/ClassLoader 隔离） |
| `org.codehaus.groovy:groovy-all` | 2.4.21 | ~7MB   | 脚本引擎（Groovy，语法兼容 Java）        |
| `com.googlecode.aviator:aviator` | 5.3.3  | ~500KB | 表达式引擎（字段计算/条件判断）          |

---

## 十一、风险与对策

| 风险          | 等级 | 对策                                                               |
| ------------- | ---- | ------------------------------------------------------------------ |
| SQL 注入      | 高   | custom 用参数化占位符；SqlFieldValidator 校验表/列                 |
| 越权字段      | 高   | FieldGuard 模型声明白名单过滤                                      |
| 全表误删/误改 | 高   | delete/update 强制 conditions 非空                                 |
| 批量 DoS      | 中   | max_batch_size 限制（默认 1000），pageSize 上限 200                |
| 批量转换性能  | 高   | BatchFieldTransformer 批量预查询+内存映射，复用 BatchQueryStrategy |
| 触发环        | 高   | ActionContext.triggerChain 检测重复中止                            |
| 同步动作失败  | 中   | rollback 回退状态+记日志；脚本/插件里自行保证一致性                |
| 脚本/插件滥用 | 中   | 先不限制，全量审计日志（lowcode_trigger_log）                      |
| 对外裸奔      | 中   | 第一版可开关 Token，第二版接网关                                   |
| 缓存不一致    | 中   | 变更 evict L1 + RocketMQ 跨实例广播                                |

---

## 十二、阶段边界

| 阶段   | 能做                                                                            | 不做（后续）                                                                      |
| ------ | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| 第一版 | 元数据驱动 CRUD、字段转换、状态触发、7种动作+脚本+插件、线性审批、同步/异步分离 | 会签/分支审批、role/dynamic 审批人、网关鉴权/限流/签名、SSRF 白名单、严格事务回滚 |
| 第二版 | 会签/分支、审批人解析、网关 JWT+限流、SSRF 防护                                 | -                                                                                 |

---

## 十三、附录：现有复用资产清单

| 资产                      | 复用于                          |
| ------------------------- | ------------------------------- |
| SqlEngineFacade           | 执行引擎内核                    |
| SqlFieldValidator         | 表/列合法性校验                 |
| SqlTemplateEngine         | custom SQL 模板渲染             |
| LowcodeHelper             | 元数据表 CRUD（dbName=lowcode） |
| L1CaffeineCache           | 元数据缓存 + code→id L1         |
| L2RedisCache              | code→id L2（Redisson 管道化）   |
| KeyBuilder                | 缓存 key 构造                   |
| BatchQueryStrategy        | 批量 IN 查询回源                |
| CacheInvalidationListener | RocketMQ 跨实例失效             |
| ResMsg                    | 统一返回                        |
| LowcodeTableInitializer   | DDL 自动执行                    |
| RocketMQTemplate          | 异步动作 + 跨实例广播           |
| MinioService              | 插件 JAR 存储                   |
| NacosConfigService        | 插件配置广播 + 各服务监听       |

---

## 十四、同步动作事务化（v4.0 新增）

### 14.1 问题

之前设计：主 update 先执行 → 同步动作后执行 → 失败只回退状态，**非严格事务回滚**，可能数据不一致。

### 14.2 方案：编译收集 + 一次性事务提交

写操作不直接执行，编译成 `CompiledSql` 收集到 `sqlList`，最后 `executeInTransaction` 一次性提交。读操作直接执行（不进事务）。

### 14.3 PlatformApi 接口调整

```java
public interface PlatformApi {
    // 读操作：直接执行（不进事务）
    JSONArray query(JSONObject req, String dbName) throws Exception;
    long count(JSONObject req, String dbName) throws Exception;

    // 写操作编译模式：返回 CompiledSql，不执行（加入事务）
    CompiledSql compileInsert(JSONObject req, String dbName) throws Exception;
    CompiledSql compileUpdate(JSONObject req, String dbName) throws Exception;
    CompiledSql compileDelete(JSONObject req, String dbName) throws Exception;

    // 事务执行
    void executeInTransaction(String dbName, List<CompiledSql> sqlList) throws Exception;

    // 其他
    String redisGet(String key);
    void redisSet(String key, String value, long ttlSec);
    void sendMq(String topic, Object message);
    JSONObject getModel(String modelCode) throws Exception;
}
```

### 14.4 ActionContext 加 sqlList

```java
public class ActionContext {
    // ... 原有字段 ...
    private List<CompiledSql> sqlList = new ArrayList<>();
    public void addSql(CompiledSql sql) { sqlList.add(sql); }
}
```

### 14.5 MetaEngineService.doUpdate 改造

```java
private int doUpdate(...) throws Exception {
    // ... 校验、白名单、字段转换 ...

    // 1. 编译主 update SQL（不直接执行）
    CompiledSql mainSql = platformApi.compileUpdate(req, dbName);

    // 2. 收集到 sqlList
    List<CompiledSql> sqlList = new ArrayList<>();
    sqlList.add(mainSql);

    // 3. 状态变更检测 + 同步动作编译（动作用 compileXxx 编译，加入 sqlList）
    ActionContext ctx = new ActionContext();
    ctx.setSqlList(sqlList);
    ctx.setDbName(dbName);
    ctx.setNewData(data);
    statusChangeDetector.detectAndCompile(modelCode, tableName, statusField,
        conditions, data, ctx);

    // 4. 一次性事务执行（主操作 + 所有同步动作，原子提交）
    platformApi.executeInTransaction(dbName, sqlList);

    // 5. 异步动作发 MQ（事务提交成功后才发）
    statusChangeDetector.fireAsync(ctx);

    return 1;
}
```

### 14.6 SqlEngineFacade 需加 compile 方法

```java
// bcd-wms-db SqlEngineFacade 新增（扩展，不改现有方法）
public CompiledSql compileInsert(JSONObject req, String dbName) throws Exception {
    // 复用现有 insert 编译逻辑（字段校验、autoTimestamp 等），返回 CompiledSql 不执行
}
public CompiledSql compileUpdate(JSONObject req, String dbName) throws Exception { ... }
public CompiledSql compileDelete(JSONObject req, String dbName) throws Exception { ... }
```

### 14.7 执行流程对比

| 之前                                     | 改造后                                                              |
| ---------------------------------------- | ------------------------------------------------------------------- |
| 主 update 执行 → 动作执行 → 失败回退状态 | 主 update **编译** → 动作**编译** → **一次性 executeInTransaction** |
| 非原子，可能不一致                       | **原子，全成功或全回滚**                                            |

---

## 十五、微服务插件分发（v4.0 新增）

### 15.1 问题

微服务架构（Nacos + RocketMQ + MinIO），插件 JAR 怎么分发到多个服务实例？怎么保证一致性？怎么热更新？

### 15.2 方案：MinIO 存储 + Nacos 广播 + 各服务监听拉取

```
上传 JAR → MinIO + lowcode_plugin 表 + Nacos 配置
  ↓ Nacos 广播
各服务监听 → 按 target_services 过滤 → checksum 对比 → MinIO 下载 → 热加载
```

### 15.3 lowcode_plugin 表扩展

```sql
ALTER TABLE lowcode_plugin ADD COLUMN IF NOT EXISTS jar_url         VARCHAR(512);
ALTER TABLE lowcode_plugin ADD COLUMN IF NOT EXISTS target_services VARCHAR(512);
ALTER TABLE lowcode_plugin ADD COLUMN IF NOT EXISTS checksum        VARCHAR(128);
```

### 15.4 Nacos 配置（dataId: lowcode.plugins, group: LOWCODE）

```json
{
  "plugins": [
    {
      "pluginId": "stock-adjust",
      "version": "1.0.0",
      "jarUrl": "minio://lowcode/plugins/stock-adjust-1.0.0.jar",
      "checksum": "abc123def456",
      "targetServices": ["bcd-code", "bcd-wms"],
      "enabled": true
    }
  ]
}
```

### 15.5 各服务监听 Nacos + 热加载

```java
@Service
public class PluginSyncService {
    @Resource private PluginManagerService pluginManager;
    @Resource private MinioService minioService;

    @NacosConfigListener(dataId = "lowcode.plugins", groupId = "LOWCODE")
    public void onPluginConfigChanged(String config) {
        JSONObject cfg = JSON.parseObject(config);
        JSONArray plugins = cfg.getJSONArray("plugins");
        String currentService = ApplicationContextHolder.getServiceName();

        for (int i = 0; i < plugins.size(); i++) {
            JSONObject plugin = plugins.getJSONObject(i);
            String pluginId = plugin.getString("pluginId");

            // 1. 过滤：只加载 targetServices 包含当前服务的插件
            JSONArray targets = plugin.getJSONArray("targetServices");
            if (!targets.contains(currentService)) continue;

            // 2. 启停
            if (!plugin.getBooleanValue("enabled")) {
                pluginManager.unloadPlugin(pluginId); continue;
            }

            // 3. checksum 对比
            String checksum = plugin.getString("checksum");
            if (!needUpdate(pluginId, checksum)) continue;

            // 4. 从 MinIO 下载 JAR
            File jarFile = minioService.download(plugin.getString("jarUrl"));

            // 5. 卸载旧版 + 热加载新版
            pluginManager.unloadPlugin(pluginId);
            pluginManager.loadPlugin(jarFile);
            updateLocalRecord(pluginId, checksum);
        }
    }
}
```

### 15.6 分发流程

| 步骤 | 动作                                         | 触发方式         |
| ---- | -------------------------------------------- | ---------------- |
| 上传 | JAR → MinIO + lowcode_plugin 表 + Nacos 配置 | 设计器插件管理页 |
| 分发 | Nacos 广播 → 各服务监听                      | Nacos 自动       |
| 拉取 | 按 target_services 过滤 → MinIO 下载         | 监听到变更       |
| 加载 | PluginManagerService 热加载                  | 下载完成后       |
| 更新 | checksum 对比 → 卸载旧版 + 加载新版          | 自动             |
| 启停 | Nacos enabled=false → 各服务卸载             | 设计器操作       |

### 15.7 新增类

```
plugin/
├── PluginManagerService.java    -- 热加载/卸载
├── PluginSyncService.java       -- Nacos 监听 + MinIO 下载
└── PluginController.java        -- 上传/启停/列表
```
