# 元数据驱动动态 API 设计文档

> 版本：v1.0  
> 日期：2026-09-21  
> 模块：bcd-code（端口 3015）  
> 数据源：lowcode（元数据库）/ project（业务库）

---

## 一、背景与目标

### 1.1 背景

低代码平台前端设计器已能产出页面元数据（`lowcode_page.schema_json`），描述页面组件树、字段、按钮等渲染协议。后端 `bcd-code` 微服务已有 `SqlEngineFacade`（JSON 驱动 SQL 引擎）+ `LowcodeHelper` + `lowcode_api_registry`（API 登记簿）。

当前缺口：

- 无独立的数据模型元数据表（`lowcode_model`），字段定义散落在页面 schema 里
- `lowcode_api_registry` 仅作登记簿，无运行时路由消费，字段单薄
- 无对外统一入口，`SqlEngineController` 返回裸 JSON 无鉴权无 ResMsg 包装
- 无字段转换机制（外部传 code → 内部存 id）

### 1.2 目标

通过元数据驱动，实现：

1. **零代码新增 CRUD 接口**：设计器配置数据模型 + API 元数据 → 自动生成可调用接口
2. **对外 API 直接发布**：`/api/open/**` 统一入口，自动生成 OpenAPI 文档
3. **字段自动转换**：外部传编码（code）→ 内部自动查表转 ID，支持批量高性能转换
4. **复用现有基础设施**：`SqlEngineFacade` + 三级缓存（L1Caffeine → L2Redis → DB）+ `BatchQueryStrategy`

---

## 二、现状分析

### 2.1 后端现状

| 项       | 现状                                                                                                                                                  |
| -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 包结构   | `com.api.lowcode.*`，每模块 Controller+Service，无 Entity/Mapper                                                                                      |
| 数据访问 | `LowcodeHelper`（封装 `SqlEngineFacade`，dbName 硬编码 `"lowcode"`）                                                                                  |
| SQL 引擎 | `SqlEngineFacade`：query/insert/update/delete/batchInsert/batchUpdate/count，全参数化，`SqlFieldValidator` 白名单校验，`SqlTemplateEngine` 模板占位符 |
| 统一返回 | `ResMsg`（res/code/msg/errtype/data/customValue），无静态工厂，msg 硬编码中文                                                                         |
| 接口风格 | 统一 POST + `@RequestBody JSONObject` + ResMsg 包装                                                                                                   |
| 事务     | 无 `@Transactional`，用 `SqlEngineFacade.executeInTransaction`                                                                                        |
| 国际化   | 纯 DB 驱动（`lowcode_lang` + `lowcode_lang_text`），后端提供字典前端替换，`ResMsg.msg` 不走国际化                                                     |

### 2.2 现有缓存设施（全部复用，不重复造轮子）

| 设施                        | 路径                                                  | 复用于                                       |
| --------------------------- | ----------------------------------------------------- | -------------------------------------------- |
| `L1CaffeineCache`           | `com.server.basedata.cache.L1CaffeineCache`           | 元数据缓存 + code→id L1 缓存                 |
| `L2RedisCache`              | `com.server.basedata.cache.L2RedisCache`              | code→id L2 缓存（Redisson 管道化 mget/mset） |
| `KeyBuilder`                | `com.server.basedata.cache.KeyBuilder`                | 缓存 key 构造 `bd:<cacheName>:s<ver>:<id>`   |
| `BatchQueryStrategy`        | `com.server.basedata.database.BatchQueryStrategy`     | 批量 IN 查询回源（默认 1000/批）             |
| `CacheInvalidationListener` | `com.server.basedata.event.CacheInvalidationListener` | RocketMQ 跨实例缓存失效广播                  |
| `SqlFieldValidator`         | `com.server.sqlengine.validator.SqlFieldValidator`    | 表列结构校验（已有 5min TTL 缓存）           |
| `BreakdownGuard`            | `com.server.basedata.protection.BreakdownGuard`       | 熔断/单飞/空值缓存防护                       |

### 2.3 现有表结构

| 表                                   | 用途       | 关键字段                                                        |
| ------------------------------------ | ---------- | --------------------------------------------------------------- |
| `lowcode_page`                       | 页面元数据 | page_id, schema_json(TEXT), table_name, enabled, deleted        |
| `lowcode_api_registry`               | API 登记簿 | id, url, method, perm_code, description, group_name, updated_at |
| `lowcode_lang` / `lowcode_lang_text` | 多语言     | locale, text_key, text_val                                      |

---

## 三、整体架构

```
┌──────────────────────────────────────────────────────────────┐
│  外部消费者（第三方系统 / 内部其他微服务 / 移动端）              │
└───────────────────────────┬──────────────────────────────────┘
                            │ POST /api/open/**
┌───────────────────────────▼──────────────────────────────────┐
│  OpenApiController                                           │
│  Token 校验 → 提取 path → 查 API 元数据(缓存) → access log    │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  MetaEngineService（核心执行引擎）                             │
│                                                               │
│  ① FieldGuard        → 字段白名单过滤（防越权）                  │
│  ② FieldTransformer   → 单条：字段重命名 + code→id 转换         │
│  ③ BatchFieldTransformer → 批量：批量预查询 + 内存映射          │
│  ④ conditions 校验    → delete/update 强制非空                  │
│  ⑤ SqlEngineFacade    → 编译参数化 SQL → 执行                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
    lowcode 数据源       project 数据源       L1+L2 缓存
   （元数据表）        （业务表 CRUD）     （code→id 映射）
```

### 数据流

```
设计器 ──配置──▶ lowcode_model（数据模型元数据）
         ──配置──▶ lowcode_api_registry（API 元数据，含字段映射规则）
                              │
外部调用 /api/open/** ──▶ OpenApiController
                              │
                   MetaEngineService.execute
                              │
              读元数据(缓存) → 字段白名单 → 字段转换 → 执行 SQL
                              │
                        ResMsg 返回
```

---

## 四、详细设计

### 4.1 数据库设计（DDL）

追加到 `bcd-code/src/main/resources/db/lowcode_tables.sql`：

#### 4.1.1 数据模型元数据表

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

字段说明：

- `db_name`：数据源名（project=业务库, lowcode=元数据库）
- `fields_json`：字段元数据数组（name 用表列名 snake_case）
- `cache_enabled`：是否启用 code→id 缓存（仓库=true, 物料=false）
- `cache_name`：缓存命名空间（对应 L1CaffeineCache 的 key）
- `code_column` / `id_column`：该模型的编码列/主键列（用于 code→id 转换）

`fields_json` 结构示例：

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
    "name": "order_no",
    "type": "string",
    "length": 32,
    "nullable": false,
    "comment": "单据号"
  },
  {
    "name": "warehouse_id",
    "type": "string",
    "length": 64,
    "nullable": true,
    "comment": "仓库ID",
    "ref": {
      "model": "wms_warehouse",
      "idColumn": "warehouse_id",
      "codeColumn": "warehouse_code"
    }
  },
  {
    "name": "status",
    "type": "string",
    "length": 16,
    "nullable": false,
    "comment": "状态"
  }
]
```

#### 4.1.2 扩展 API 注册表

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

字段说明：

- `operation`：query / pageQuery / insert / batchInsert / update / batchUpdate / delete / custom
- `param_mapping`：字段映射规则 JSON（外部字段 → 转换 → 内部字段）
- `sql_template`：custom 操作的 SQL 模板（`@{alias.field}@` 占位符）
- `max_batch_size`：批量操作行数上限（防 DoS）

`param_mapping` 结构示例：

```json
{
  "warehouseCode": {
    "targetField": "warehouse_id",
    "transform": "codeToId",
    "refModel": "wms_warehouse"
  },
  "materialCode": {
    "targetField": "material_id",
    "transform": "codeToId",
    "refModel": "wms_material"
  },
  "orderNo": { "targetField": "order_no", "transform": "direct" },
  "status": { "targetField": "status", "transform": "direct" }
}
```

### 4.2 核心类设计

新增文件均在 `bcd-code/src/main/java/com/api/lowcode/`：

```
engine/
├── MetaEngineService.java       -- 核心执行引擎
├── OpenApiController.java       -- 对外统一入口
├── FieldGuard.java              -- 字段白名单过滤
├── FieldTransformer.java        -- 单条字段转换
├── BatchFieldTransformer.java   -- 批量字段转换
├── SqlTemplateGuard.java        -- SQL 模板安全校验
├── MetaCacheService.java        -- 元数据缓存（复用 L1CaffeineCache）
└── RefDataCacheService.java     -- code→id 缓存（复用 L1+L2+BatchQueryStrategy）
model/
├── ModelController.java         -- 数据模型管理
└── ModelService.java            -- 复用 LowcodeHelper
```

#### 4.2.1 MetaEngineService

```java
@Service
public class MetaEngineService {
    @Resource private MetaCacheService metaCache;
    @Resource private FieldGuard fieldGuard;
    @Resource private FieldTransformer fieldTransformer;
    @Resource private BatchFieldTransformer batchFieldTransformer;
    @Resource private SqlTemplateGuard sqlTemplateGuard;
    @Resource private SqlEngineFacade facade;

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
            case "batchUpdate":return doBatchUpdate(apiMeta, body, dbName, fields, paramMapping);
            case "delete":     return doDelete(apiMeta, body, dbName, fields);
            case "custom":     return doCustom(apiMeta, body, dbName, fields);
            default: throw new IllegalArgumentException("不支持的操作: " + operation);
        }
    }

    private String resolveDbName(JSONObject apiMeta) throws Exception {
        String dbName = apiMeta.getString("dbName");
        if (dbName == null || dbName.isEmpty()) {
            JSONObject model = metaCache.getModel(apiMeta.getString("modelCode"));
            dbName = model.getString("dbName");
        }
        return dbName;
    }

    // 标准 query
    private JSONArray doQuery(JSONObject api, JSONObject body, String dbName, JSONArray fields) throws Exception {
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("conditions", body.getJSONArray("conditions"));
        req.put("orderBy", body.getJSONArray("orderBy"));
        req.put("columns", fieldGuard.filterColumns(body.getJSONArray("columns"), fields));
        return facade.query(req, dbName);
    }

    // 分页 query
    private JSONObject doPageQuery(JSONObject api, JSONObject body, String dbName, JSONArray fields) throws Exception {
        int page = body.getIntValue("page", 1);
        int pageSize = Math.min(body.getIntValue("pageSize", 20), 200);
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("conditions", body.getJSONArray("conditions"));
        req.put("columns", fieldGuard.filterColumns(body.getJSONArray("columns"), fields));
        req.put("page", page);
        req.put("pageSize", pageSize);
        JSONArray list = facade.query(req, dbName);
        long total = facade.count(req, dbName);
        JSONObject result = new JSONObject();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    // 单条 insert
    private int doInsert(JSONObject api, JSONObject body, String dbName, JSONArray fields, JSONObject paramMapping) throws Exception {
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject data = body.getJSONObject("data");
        data = fieldGuard.filterData(data, fields);
        data = fieldTransformer.transform(data, paramMapping, dbName);
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("data", data);
        return facade.insert(req, dbName);
    }

    // 批量 insert
    private int[] doBatchInsert(JSONObject api, JSONObject body, String dbName, JSONArray fields, JSONObject paramMapping) throws Exception {
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONArray rows = body.getJSONArray("data");
        int maxBatch = api.getIntValue("maxBatchSize", 1000);
        if (rows.size() > maxBatch) {
            throw new IllegalArgumentException("批量操作超过上限: " + rows.size() + " > " + maxBatch);
        }
        fieldGuard.filterBatch(rows, fields);
        batchFieldTransformer.transformBatch(rows, paramMapping, dbName);
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("data", rows);
        return facade.batchInsert(req, dbName);
    }

    // update（conditions 强制非空）
    private int doUpdate(JSONObject api, JSONObject body, String dbName, JSONArray fields, JSONObject paramMapping) throws Exception {
        JSONArray conditions = body.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("update 操作 conditions 不能为空，禁止全表更新");
        }
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject data = body.getJSONObject("data");
        data = fieldGuard.filterData(data, fields);
        data = fieldTransformer.transform(data, paramMapping, dbName);
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("data", data);
        req.put("conditions", conditions);
        return facade.update(req, dbName);
    }

    // delete（conditions 强制非空）
    private int doDelete(JSONObject api, JSONObject body, String dbName, JSONArray fields) throws Exception {
        JSONArray conditions = body.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("delete 操作 conditions 不能为空，禁止全表删除");
        }
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject req = new JSONObject();
        req.put("tableName", model.getString("tableName"));
        req.put("conditions", conditions);
        return facade.delete(req, dbName);
    }

    // custom（sqlTemplate 安全校验）
    private JSONArray doCustom(JSONObject api, JSONObject body, String dbName, JSONArray fields) throws Exception {
        String sqlTemplate = api.getString("sqlTemplate");
        sqlTemplateGuard.check(sqlTemplate);
        JSONObject model = metaCache.getModel(api.getString("modelCode"));
        JSONObject req = new JSONObject();
        req.put("sqlTemplate", sqlTemplate);
        req.put("conditions", body.getJSONArray("conditions"));
        return facade.query(req, dbName);
    }
}
```

#### 4.2.2 OpenApiController

```java
@RestController
@RequestMapping("/api/open")
public class OpenApiController {
    @Resource private MetaCacheService metaCache;
    @Resource private MetaEngineService engine;

    @Value("${lowcode.open-api-token:}")
    private String openApiToken;

    @PostMapping("/**")
    public ResMsg dispatch(HttpServletRequest request,
                           @RequestBody(required = false) JSONObject body) {
        ResMsg r = new ResMsg();
        long start = System.currentTimeMillis();
        String path = request.getRequestURI().substring("/api/open".length());
        try {
            // 可开关 Token 校验
            if (openApiToken != null && !openApiToken.isEmpty()) {
                String token = request.getHeader("X-Api-Token");
                if (!openApiToken.equals(token)) {
                    r.setRes(false); r.setCode("401"); r.setMsg("无效的API Token");
                    return r;
                }
            }
            JSONObject apiMeta = metaCache.getApi(path, "POST");
            if (apiMeta == null) {
                r.setRes(false); r.setCode("404"); r.setMsg("接口不存在: " + path);
                return r;
            }
            if (!apiMeta.getBooleanValue("enabled")) {
                r.setRes(false); r.setCode("403"); r.setMsg("接口已停用");
                return r;
            }
            r.setData(engine.execute(apiMeta, body == null ? new JSONObject() : body));
        } catch (Exception e) {
            r.setRes(false); r.setCode("500"); r.setMsg(e.getMessage());
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 1000) {
                log.warn("[OPEN-API-SLOW] path={} cost={}ms", path, cost);
            } else {
                log.info("[OPEN-API] path={} cost={}ms", path, cost);
            }
        }
        return r;
    }
}
```

#### 4.2.3 FieldGuard

```java
@Service
public class FieldGuard {
    // 单条：只保留 model 声明的字段
    public JSONObject filterData(JSONObject data, JSONArray modelFields) {
        if (data == null || modelFields == null) return data;
        Set<String> allowed = new HashSet<>();
        for (int i = 0; i < modelFields.size(); i++) {
            allowed.add(modelFields.getJSONObject(i).getString("name"));
        }
        JSONObject filtered = new JSONObject();
        for (String key : data.keySet()) {
            if (allowed.contains(key)) filtered.put(key, data.get(key));
        }
        return filtered;
    }

    // 批量
    public void filterBatch(JSONArray rows, JSONArray modelFields) {
        for (int i = 0; i < rows.size(); i++) {
            rows.set(i, filterData(rows.getJSONObject(i), modelFields));
        }
    }

    // 查询列白名单：取 body columns 与 model fields 的交集
    public JSONArray filterColumns(JSONArray columns, JSONArray modelFields) {
        if (columns == null || columns.isEmpty()) return null; // null = SELECT 全部声明字段
        Set<String> allowed = new HashSet<>();
        for (int i = 0; i < modelFields.size(); i++) {
            allowed.add(modelFields.getJSONObject(i).getString("name"));
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.getString(i);
            if (allowed.contains(col)) result.add(col);
        }
        return result;
    }
}
```

#### 4.2.4 FieldTransformer（单条转换）

```java
@Service
public class FieldTransformer {
    @Resource private RefDataCacheService refDataCache;

    public JSONObject transform(JSONObject data, JSONObject paramMapping, String dbName) throws Exception {
        if (paramMapping == null || paramMapping.isEmpty()) return data;
        JSONObject result = new JSONObject();
        for (String extField : data.keySet()) {
            Object value = data.get(extField);
            JSONObject rule = paramMapping.getJSONObject(extField);
            if (rule == null) { result.put(extField, value); continue; }
            String targetField = rule.getString("targetField");
            String transform = rule.getString("transform");
            if ("direct".equals(transform)) {
                result.put(targetField, value);
            } else if ("codeToId".equals(transform)) {
                String refModel = rule.getString("refModel");
                String id = refDataCache.codeToId(refModel, (String) value, dbName);
                if (id == null) throw new IllegalArgumentException("编码 [" + value + "] 在 " + refModel + " 中不存在");
                result.put(targetField, id);
            } else if ("idToCode".equals(transform)) {
                String refModel = rule.getString("refModel");
                String code = refDataCache.idToCode(refModel, (String) value, dbName);
                result.put(targetField, code);
            }
        }
        return result;
    }
}
```

#### 4.2.5 BatchFieldTransformer（批量转换，核心性能优化）

```java
@Service
public class BatchFieldTransformer {
    @Resource private RefDataCacheService refDataCache;

    public void transformBatch(JSONArray rows, JSONObject paramMapping, String dbName) throws Exception {
        if (paramMapping == null || paramMapping.isEmpty()) return;

        // 1. 收集所有需要 codeToId 的值，按 refModel 分组
        Map<String, Set<String>> codesToLookup = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            for (String extField : paramMapping.keySet()) {
                JSONObject rule = paramMapping.getJSONObject(extField);
                if ("codeToId".equals(rule.getString("transform"))) {
                    String code = row.getString(extField);
                    if (code != null) {
                        codesToLookup.computeIfAbsent(rule.getString("refModel"), k -> new HashSet<>()).add(code);
                    }
                }
            }
        }

        // 2. 批量查询，构建 code→id 映射（复用 RefDataCacheService 内部的 L1→L2→DB 链路）
        Map<String, Map<String, String>> lookupMaps = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : codesToLookup.entrySet()) {
            Map<String, String> codeToId = refDataCache.batchCodeToId(entry.getKey(), entry.getValue(), dbName);
            lookupMaps.put(entry.getKey(), codeToId);
        }

        // 3. 遍历行，内存替换 + 字段重命名
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            JSONObject transformed = new JSONObject();
            for (String extField : paramMapping.keySet()) {
                JSONObject rule = paramMapping.getJSONObject(extField);
                String targetField = rule.getString("targetField");
                String transform = rule.getString("transform");
                Object value = row.get(extField);
                if ("direct".equals(transform)) {
                    transformed.put(targetField, value);
                } else if ("codeToId".equals(transform)) {
                    String refModel = rule.getString("refModel");
                    String id = lookupMaps.get(refModel).get(value);
                    if (id == null) {
                        throw new IllegalArgumentException("第" + (i + 1) + "行：编码 [" + value + "] 在 " + refModel + " 中不存在");
                    }
                    transformed.put(targetField, id);
                }
            }
            // 保留未在 paramMapping 中声明的字段
            for (String key : row.keySet()) {
                if (!paramMapping.containsKey(key)) transformed.put(key, row.get(key));
            }
            rows.set(i, transformed);
        }
    }
}
```

#### 4.2.6 SqlTemplateGuard

```java
@Service
public class SqlTemplateGuard {
    private static final Set<String> BLACKLIST = new HashSet<>(Arrays.asList(
        "DROP", "TRUNCATE", "ALTER", "GRANT", "REVOKE", "CREATE", "DELETE FROM", "UPDATE "
    ));

    public void check(String sqlTemplate) {
        if (sqlTemplate == null || sqlTemplate.isEmpty()) return;
        String upper = sqlTemplate.toUpperCase();
        for (String keyword : BLACKLIST) {
            if (upper.contains(keyword)) {
                throw new IllegalArgumentException("SQL 模板包含禁止的关键字: " + keyword);
            }
        }
    }
}
```

### 4.3 缓存设计（全部复用现有设施）

#### 4.3.1 MetaCacheService（元数据缓存）

复用 `L1CaffeineCache`，缓存 `lowcode_model` 和 `lowcode_api_registry` 查询结果。

```java
@Service
public class MetaCacheService {
    @Resource private LowcodeHelper helper;
    @Resource private L1CaffeineCache metaL1Cache;  // 复用现有 L1CaffeineCache

    private static final String MODEL_PREFIX = "meta:model:";
    private static final String API_PREFIX = "meta:api:";

    public JSONObject getModel(String modelCode) throws Exception {
        String key = MODEL_PREFIX + modelCode;
        JSONObject cached = (JSONObject) metaL1Cache.get(key);
        if (cached != null) return cached;
        // 回源查 lowcode_model
        JSONArray rows = helper.queryAndMap("lowcode_model", ALL_COLS,
            helper.eq("model_code", modelCode), null, false, MODEL_MAPPING);
        if (rows.isEmpty()) throw new IllegalArgumentException("数据模型不存在: " + modelCode);
        JSONObject model = rows.getJSONObject(0);
        metaL1Cache.put(key, model);
        return model;
    }

    public JSONObject getApi(String url, String method) throws Exception {
        String key = API_PREFIX + method + ":" + url;
        JSONObject cached = (JSONObject) metaL1Cache.get(key);
        if (cached != null) return cached;
        JSONArray rows = helper.queryAndMap("lowcode_api_registry", API_COLS,
            helper.eq("url", url), "updated_at", true, API_MAPPING);
        // 过滤 method 匹配
        JSONObject api = null;
        for (int i = 0; i < rows.size(); i++) {
            if (method.equals(rows.getJSONObject(i).getString("method"))) {
                api = rows.getJSONObject(i); break;
            }
        }
        if (api != null) metaL1Cache.put(key, api);
        return api;
    }

    // 元数据变更时调用（ModelService/ApiRegistryService save 后触发）
    public void evictModel(String modelCode) { metaL1Cache.invalidate(MODEL_PREFIX + modelCode); }
    public void evictApi(String url, String method) { metaL1Cache.invalidate(API_PREFIX + method + ":" + url); }
    public void evictAll() { metaL1Cache.invalidateAll(); }
}
```

缓存失效广播：元数据变更时 evict L1 + 通过 `CacheInvalidationListener`（RocketMQ）广播跨实例失效。

#### 4.3.2 RefDataCacheService（code→id 缓存）

复用 `L1CaffeineCache`（L1）+ `L2RedisCache`（L2）+ `BatchQueryStrategy`（回源）+ `KeyBuilder`（key 构造）。

```java
@Service
public class RefDataCacheService {
    @Resource private L1CaffeineCache refL1Cache;       // 复用
    @Resource private L2RedisCache l2RedisCache;         // 复用
    @Resource private BatchQueryStrategy batchQueryStrategy; // 复用
    @Resource private MetaCacheService metaCache;
    @Resource private SqlEngineFacade facade;

    // 单条 code→id
    public String codeToId(String refModelCode, String code, String dbName) throws Exception {
        JSONObject model = metaCache.getModel(refModelCode);
        String cacheName = model.getString("cacheName");
        String key = KeyBuilder.buildKey(cacheName, 1, code);

        // L1
        String id = (String) refL1Cache.get(key);
        if (id != null) return id;

        // L2
        id = l2RedisCache.get(key);
        if (id != null) { refL1Cache.put(key, id); return id; }

        // DB 回源
        id = lookupByCode(model, code, dbName);
        if (id != null) {
            refL1Cache.put(key, id);
            l2RedisCache.set(key, id, 3600); // L2 TTL 1h
        }
        return id;
    }

    // 批量 code→id（核心：一次 IN 查询，不走逐条）
    public Map<String, String> batchCodeToId(String refModelCode, Set<String> codes, String dbName) throws Exception {
        JSONObject model = metaCache.getModel(refModelCode);
        String cacheName = model.getString("cacheName");
        String codeColumn = model.getString("codeColumn");
        String idColumn = model.getString("idColumn");
        String tableName = model.getString("tableName");

        Map<String, String> result = new HashMap<>();
        Set<String> missCodes = new HashSet<>(codes);

        // L1 命中
        for (String code : codes) {
            String key = KeyBuilder.buildKey(cacheName, 1, code);
            String id = (String) refL1Cache.get(key);
            if (id != null) { result.put(code, id); missCodes.remove(code); }
        }

        // L2 命中（管道化 mget）
        if (!missCodes.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String code : missCodes) keys.add(KeyBuilder.buildKey(cacheName, 1, code));
            List<String> values = l2RedisCache.mget(keys);
            int idx = 0;
            for (String code : missCodes) {
                String id = values.get(idx++);
                if (id != null) { result.put(code, id); refL1Cache.put(KeyBuilder.buildKey(cacheName, 1, code), id); }
            }
        }

        // DB 回源（复用 BatchQueryStrategy，自动分批 IN 查询）
        Set<String> stillMiss = new HashSet<>();
        for (String code : missCodes) if (!result.containsKey(code)) stillMiss.add(code);
        if (!stillMiss.isEmpty()) {
            // SELECT codeColumn, idColumn FROM tableName WHERE codeColumn IN (?,?,...)
            JSONArray rows = batchQueryStrategy.batchQuery(
                tableName, codeColumn, new String[]{codeColumn, idColumn}, stillMiss, dbName);
            Map<String, String> kvMap = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject r = rows.getJSONObject(i);
                String code = r.getString(codeColumn.toUpperCase());
                String id = r.getString(idColumn.toUpperCase());
                result.put(code, id);
                kvMap.put(KeyBuilder.buildKey(cacheName, 1, code), id);
                refL1Cache.put(KeyBuilder.buildKey(cacheName, 1, code), id);
            }
            // 回填 L2（管道化）
            if (!kvMap.isEmpty()) l2RedisCache.pipelineSet(kvMap, 3600);
        }
        return result;
    }

    private String lookupByCode(JSONObject model, String code, String dbName) throws Exception {
        String tableName = model.getString("tableName");
        String codeColumn = model.getString("codeColumn");
        String idColumn = model.getString("idColumn");
        JSONObject req = new JSONObject();
        req.put("tableName", tableName);
        req.put("columns", new JSONArray().fluentAdd(idColumn));
        req.put("conditions", buildEqCondition(codeColumn, code));
        JSONArray rows = facade.query(req, dbName);
        if (rows.isEmpty()) return null;
        return rows.getJSONObject(0).getString(idColumn.toUpperCase());
    }

    private JSONArray buildEqCondition(String column, Object value) {
        JSONArray conditions = new JSONArray();
        conditions.add(new JSONObject().fluentPut("Symbol", "and"));
        conditions.add(new JSONObject()
            .fluentPut("Id", column)
            .fluentPut("Symbol", "=")
            .fluentPut("Val", value)
            .fluentPut("TableAlias", "T"));
        return conditions;
    }
}
```

#### 4.3.3 缓存分层策略

| 基础数据  | 数据量  | L1(Caffeine) | L2(Redis) | 回源     | 配置                                                         |
| --------- | ------- | ------------ | --------- | -------- | ------------------------------------------------------------ |
| 仓库      | < 1 万  | 全量缓存     | 缓存      | 启动加载 | `cache_enabled=true, cache_name=wms_warehouse`               |
| 物料      | 1000 万 | 不全量       | 热点缓存  | 批量 IN  | `cache_enabled=false`（L1 不全量，按批次查 DB，结果回填 L2） |
| 单位/币种 | < 100   | 全量缓存     | 缓存      | 启动加载 | `cache_enabled=true`                                         |

### 4.4 接口定义

#### 4.4.1 设计器管理接口（内部 /api/lowcode/\*）

| 接口                                | 方法   | 入参              | 出参 data        | 说明                                      |
| ----------------------------------- | ------ | ----------------- | ---------------- | ----------------------------------------- |
| `/api/lowcode/model/list`           | POST   | 无                | JSONArray        | 模型列表                                  |
| `/api/lowcode/model/{modelCode}`    | POST   | path              | JSONObject       | 单模型                                    |
| `/api/lowcode/model`                | POST   | JSONObject        | -                | upsert，保存后 evict 缓存                 |
| `/api/lowcode/model/{modelCode}`    | DELETE | path              | -                | 删除，evict 缓存                          |
| `/api/lowcode/api-registry/list`    | POST   | 无                | JSONArray        | API 列表（字段已扩展）                    |
| `/api/lowcode/api-registry`         | POST   | JSONArray         | -                | 批量保存，校验 modelCode 存在，evict 缓存 |
| `/api/lowcode/api-registry/test`    | POST   | `{apiCode, body}` | 执行结果         | API 调试器                                |
| `/api/lowcode/api-registry/openapi` | POST   | 无                | OpenAPI 3.0 JSON | 文档生成                                  |

#### 4.4.2 对外动态 API（/api/open/\*\*）

调用示例（单条新增）：

```
POST /api/open/inbound/order/save
X-Api-Token: <token>
{
  "data": { "orderNo":"W2026001", "warehouseCode":"WH01", "materialCode":"M001", "status":"draft" }
}
```

调用示例（批量新增 1000 条）：

```
POST /api/open/inbound/order/batch-save
X-Api-Token: <token>
{
  "data": [
    {"orderNo":"W001","warehouseCode":"WH01","materialCode":"M001","status":"draft"},
    {"orderNo":"W002","warehouseCode":"WH02","materialCode":"M002","status":"draft"},
    ... 1000 条
  ]
}
```

返回：

```json
{
  "res": true,
  "code": "200",
  "msg": "方法执行成功",
  "data": 1000
}
```

分页查询示例：

```
POST /api/open/inbound/order/list
{
  "conditions": [{"Symbol":"and"},{"Id":"status","Symbol":"=","Val":"draft","TableAlias":"T"}],
  "page": 1,
  "pageSize": 20
}
```

返回：

```json
{
  "res": true,
  "code": "200",
  "data": { "list": [...], "total": 128, "page": 1, "pageSize": 20 }
}
```

### 4.5 配置新增

`bcd-code/src/main/resources/application.yml`：

```yaml
lowcode:
  open-api-token: ''
  meta-cache-ttl-min: 5
  ref-cache-l2-ttl-sec: 3600
  batch-in-size: 500
```

### 4.6 前端改造（设计器 web-code）

| 改造项         | 文件                                                   | 说明                                                   |
| -------------- | ------------------------------------------------------ | ------------------------------------------------------ |
| 数据模型设计页 | `src/views/ModelDesigner.vue`（新增）                  | 编辑 lowcode_model：表名/数据源/字段列表/缓存配置      |
| API 设计页改造 | `src/views/ApiRegistry.vue` + `src/store/api-store.ts` | 扩展 operation/modelCode/paramMapping/sqlTemplate 编辑 |
| API 调试器     | `src/components/ApiTester.vue`（新增）                 | 选 apiCode → 填 body → 调 /test 预览结果               |
| 持久化         | `src/store/persistence.ts`                             | 新增 listModels/saveModel/deleteModel                  |

UI 约定：operation/modelCode 选择用触发按钮+弹窗卡片（不用下拉框），符合现有 UI 偏好。

---

## 五、多角色评审

### 5.1 产品经理

| 维度   | 评估                                                                                                                                                           |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。对外 API 发布从周级降到天级，新增接口零代码                                                                                                             |
| 关注点 | ① 第一版无网关鉴权，只能内部用，需明确对外发布时间节点；② API 文档质量依赖元数据填写，需规范约束；③ 字段转换对外部透明——外部传 code，内部自动转 id，外部无感知 |
| 建议   | API 调试器必须做，降低对接成本；request_schema/response_schema 要认真填，文档即契约                                                                            |

### 5.2 开发经理

| 维度   | 评估                                                                                                                                                                                                                                                         |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 可行性 | ✅ 高。复用 SqlEngineFacade 不重造；缓存复用 L1CaffeineCache+L2RedisCache+BatchQueryStrategy，不 new Caffeine                                                                                                                                                |
| 关注点 | ① MetaEngineService 混用 LowcodeHelper（查元数据 dbName=lowcode）和 SqlEngineFacade（执行业务 dbName=project），需注释标清；② 无 @Transactional，batchInsert 内部是 JDBC batch+手动事务，可接受；③ BatchQueryStrategy 已有 IN 分批（默认 1000/批），直接复用 |
| 建议   | 元数据缓存复用 L1CaffeineCache；code→id 缓存复用 L1+L2+BatchQueryStrategy 三级链路；跨实例失效复用 CacheInvalidationListener                                                                                                                                 |

### 5.3 安全经理

| 维度   | 评估                                                                                                                                                                                                                                                  |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ⚠️ 中。需补齐防护                                                                                                                                                                                                                                     |
| 关注点 | ① /api/open 无鉴权=裸奔，第一版加可开关 Token；② delete/update 空 conditions 全表操作；③ sqlTemplate 关键字黑名单；④ 字段白名单防越权；⑤ 批量 DoS——外部传 10000 条 batchInsert，需 max_batch_size 限制；⑥ code→id 转换时不存在 code 要拒绝而非存 null |
| 建议   | ① 批量上限 max_batch_size 默认 1000；② 转换失败整批中止；③ 批量场景 sqlTemplate 禁子查询；④ Token 校验 + access log                                                                                                                                   |

### 5.4 架构师

| 维度   | 评估                                                                                                                                                                 |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。元数据与执行解耦，开闭原则                                                                                                                                    |
| 关注点 | ① 元数据每次请求查——必须缓存，复用 L1CaffeineCache；② 缓存一致性——元数据变更 evict L1 + RocketMQ 跨实例广播；③ OpenApiController 的 /\*\* 路由需验证 Spring 5.2 匹配 |
| 建议   | 元数据变更走 ModelService/ApiRegistryService，保存后 evict + 发 RocketMQ 事件                                                                                        |

### 5.5 DBA

| 维度   | 评估                                                                                                                  |
| ------ | --------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。DDL 用 IF NOT EXISTS 可重复执行，ALTER ADD COLUMN 兼容现有数据                                                 |
| 关注点 | ① 批量 IN 查询走索引——基础数据表的 code 列必须有唯一索引；② batchInsert 对大表的影响；③ pageQuery 的 count(\*) 大表慢 |
| 建议   | 基础数据表 code 列加唯一索引；count 后续可优化为近似计数                                                              |

### 5.6 运维

| 维度   | 评估                                                                                   |
| ------ | -------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。元数据即配置，改接口不改代码不重启                                              |
| 关注点 | ① 元数据变更无重启即生效（缓存 evict）；② access log；③ 监控缓存命中率                 |
| 建议   | 复用 Caffeine recordStats() 暴露命中率；慢 SQL 阈值监控已有（SqlExecutor 默认 3000ms） |

### 5.7 评审综合结论

**方案可行**，核心基础设施已就位，改造为增量加元数据表 + 执行引擎 + 对外入口，全部复用现有 SQL 引擎和三级缓存体系。

**必须落地的安全项**：

1. delete/update 强制 conditions 非空
2. sqlTemplate 关键字黑名单
3. 批量上限 max_batch_size
4. 可开关 Token + access log
5. 字段白名单 FieldGuard
6. 转换失败整批中止

---

## 六、落地步骤

| 阶段 | 内容                                                                   | 文件                  | 依赖                                                 |
| ---- | ---------------------------------------------------------------------- | --------------------- | ---------------------------------------------------- |
| 1    | DDL：lowcode_model 建表 + lowcode_api_registry 加列                    | `lowcode_tables.sql`  | 无                                                   |
| 2    | 配置：application.yml 加 lowcode.\*                                    | `application.yml`     | 无                                                   |
| 3    | 缓存：MetaCacheService + RefDataCacheService                           | `engine/` 新增 2 文件 | 复用 L1CaffeineCache/L2RedisCache/BatchQueryStrategy |
| 4    | 安全：SqlTemplateGuard + FieldGuard                                    | `engine/` 新增 2 文件 | 无                                                   |
| 5    | 模型管理：ModelService + ModelController                               | `model/` 新增 2 文件  | 阶段 3                                               |
| 6    | API 元数据扩展：ApiRegistryService 改造 + test/openapi                 | `registry/` 改 2 文件 | 阶段 3                                               |
| 7    | 执行引擎：MetaEngineService + FieldTransformer + BatchFieldTransformer | `engine/` 新增 3 文件 | 阶段 3,4                                             |
| 8    | 对外入口：OpenApiController                                            | `engine/` 新增 1 文件 | 阶段 7                                               |
| 9    | 前端：模型设计页 + API 设计页 + 调试器                                 | `web-code`            | 阶段 5,6                                             |
| 10   | 验证：建 model + API → 调试器调通 → /api/open 调通 → 安全项验证        | -                     | 阶段 8                                               |

---

## 七、风险与对策

| 风险              | 等级 | 对策                                                                                                |
| ----------------- | ---- | --------------------------------------------------------------------------------------------------- |
| SQL 注入          | 高   | custom 用 SqlTemplateEngine 参数化占位符，禁字符串拼接；SqlFieldValidator 已做表/列校验             |
| 越权字段          | 高   | FieldGuard 对 insert/update 的 data 做模型声明白名单过滤                                            |
| 全表误删/误改     | 高   | delete/update 强制 conditions 非空                                                                  |
| 批量 DoS          | 中   | max_batch_size 限制（默认 1000），pageSize 上限 200                                                 |
| 批量转换性能      | 高   | BatchFieldTransformer 批量预查询 + 内存映射，复用 BatchQueryStrategy IN 分批，禁 sqlTemplate 子查询 |
| 对外裸奔          | 中   | 第一版可开关 Token，第二版接网关 JWT + 限流                                                         |
| 元数据缓存不一致  | 中   | 变更 evict L1 + RocketMQ 跨实例广播                                                                 |
| 基础数据表无索引  | 中   | code 列必须建唯一索引                                                                               |
| 无 @Transactional | 低   | 单表 CRUD 不涉及；多步操作用 executeInTransaction                                                   |

---

## 八、阶段边界

| 阶段             | 能做                                                                              | 不能做                                   |
| ---------------- | --------------------------------------------------------------------------------- | ---------------------------------------- |
| 第一版（本设计） | 内部系统间调用、开发联调、设计器自测、批量 CRUD + 字段转换                        | 真正对合作方开放（无网关鉴权/限流/签名） |
| 第二版（后续）   | 接 Spring Cloud Gateway，JWT 鉴权 + Redis 限流 + HMAC 签名 + perm_code 字段级权限 | -                                        |

---

## 九、附录：现有复用资产清单

| 资产                      | 路径                                                  | 复用于                          |
| ------------------------- | ----------------------------------------------------- | ------------------------------- |
| SqlEngineFacade           | `com.server.sqlengine.SqlEngineFacade`                | 执行引擎内核                    |
| SqlFieldValidator         | `com.server.sqlengine.validator.SqlFieldValidator`    | 表/列合法性校验                 |
| SqlTemplateEngine         | `com.server.sqlengine.template.SqlTemplateEngine`     | custom SQL 模板渲染             |
| LowcodeHelper             | `com.api.lowcode.common.LowcodeHelper`                | 元数据表 CRUD（dbName=lowcode） |
| L1CaffeineCache           | `com.server.basedata.cache.L1CaffeineCache`           | 元数据缓存 + code→id L1         |
| L2RedisCache              | `com.server.basedata.cache.L2RedisCache`              | code→id L2（Redisson 管道化）   |
| KeyBuilder                | `com.server.basedata.cache.KeyBuilder`                | 缓存 key 构造                   |
| BatchQueryStrategy        | `com.server.basedata.database.BatchQueryStrategy`     | 批量 IN 查询回源                |
| CacheInvalidationListener | `com.server.basedata.event.CacheInvalidationListener` | RocketMQ 跨实例失效             |
| ResMsg                    | `com.common.returns.ResMsg`                           | 统一返回                        |
| LowcodeTableInitializer   | `com.api.lowcode.LowcodeTableInitializer`             | DDL 自动执行                    |
