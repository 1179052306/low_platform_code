# bcd-wms-db API 文档

## 1. 定位与依赖

`bcd-wms-db` 是 WMS 的 Spring 基础组件包，提供动态多数据源、SQL 生成与执行、租户上下文、Redis、MinIO 和数据库方言适配能力。它**不提供 HTTP/REST Controller**；下游模块通过注入 Spring Bean 调用。

Maven 坐标：

```xml
<dependency>
    <groupId>saas-wms</groupId>
    <artifactId>bcd-wms-db</artifactId>
    <version>1.0-1</version>
</dependency>
```

核心 Bean 可通过 `DbComposite` 集中取得：`DbHelp`、`GenerateSqlImpl`、`DbValue`、`Tenant`、`SpecialHandle` 和 `MultiDataSourceHolder`。

```java
@Resource private DbHelp dbHelp;
@Resource private GenerateSqlImpl generateSql;
@Resource private DbValue dbValue;
```

`dbName` 传 `null` 时使用默认数据源；传非空值时使用命名数据源。多租户模式下，命名数据源实际按 `<dbName>-<tenant>` 查找，tenant 优先从 HTTP Header `tenant` 获取，其次使用 `MultiDataSourceHolder` 中的线程变量。

## 2. SQL 生成：`GenerateSqlImpl`

实现类为 `GenerateSql`。此 API 按表元数据生成 SQL，返回 `GenerateSqlTran`；应先检查其 `getRes()` 和 `getErrList()`，再交给 `DbHelp` 执行。

| 方法 | 用途 |
| --- | --- |
| `sqlQuery(dbName, tableName[, filters[, rpsList]])` | 根据表及过滤条件生成查询 SQL。可选 `rpsList` 用于附加 SQL 片段。 |
| `sqlQuery(dbName, tableName, sql, filters[, rpsList])` | 在自定义基础查询上追加筛选条件。 |
| `executeInsert(dbName, tableName, json[, masterMagic])` | 生成单条插入 SQL。 |
| `executeUpdate(dbName, tableName, json, wheres[, masterMagic])` | 生成更新 SQL。 |
| `executeDelete(dbName, tableName, wheres)` | 生成删除 SQL。 |
| `executeListInsert/executeListUpdate/executeDelete` | 向调用方传入的 `List<GenerateSqlTran>` 追加批处理 SQL。 |
| `add(...)` / `addContainAlias(...)` | 创建或追加查询过滤条件；后者支持表别名。 |
| `validateQuery(filters, validates)` | 按白名单校验前端传入的筛选字段。 |
| `checkParameter(dbName, column, tableName)` | 检查字段是否存在于表元数据。 |

### 2.1 查询示例

```java
JSONArray filters = generateSql.add("STATUS", "=", "ENABLE");
GenerateSqlTran tran = generateSql.sqlQuery("project", "S_ITEM", filters);
if (!tran.getRes()) {
    throw new IllegalArgumentException(tran.getErrList().toString());
}
JSONArray rows = dbHelp.queryJson("project", tran);
```

### 2.2 新增、修改与批量执行示例

```java
JSONObject item = new JSONObject();
item.put("ITEM_CODE", "SKU-001");
item.put("ITEM_NAME", "示例商品");

GenerateSqlTran insert = generateSql.executeInsert("project", "S_ITEM", item);
if (!insert.getRes()) {
    throw new IllegalArgumentException(insert.getErrList().toString());
}
int affected = dbHelp.executeSql("project", insert);

List<Wheres> wheres = Collections.singletonList(new Wheres("ITEM_CODE", "=", "SKU-001"));
GenerateSqlTran update = generateSql.executeUpdate("project", "S_ITEM", item, wheres);
```

`Wheres` 的字段包括：`paramentName`（字段名）、`symbol`（默认 `=`）、`paramentVal`（条件值）及 `isRemove`（是否参与条件）。不要将不可信输入直接拼接为 `sqlQuery` 的自定义 SQL。

## 3. SQL 执行：`DbHelp`

实现类为 `DbHelp`。所有主要方法均声明 `throws Exception`，调用方必须处理异常。查询结果的列标签会转为大写；数据库 `CLOB`/`NCLOB` 会转为字符串。

| 方法族 | 返回值 | 说明 |
| --- | --- | --- |
| `query(...)` | `DataTable` / `DataSet` | 单条或多条 SQL 的表格结果；支持 SQL、`GenerateSqlTran` 和可选 `cacheKey`。 |
| `queryJson(...)` | `JSONArray` | JSON 数组结果；支持 `cacheKey`。 |
| `queryMap(...)` | `List<Map<String,Object>>` | Map 列表结果；支持 `cacheKey`。 |
| `queryTran(...)` / `queryTranJson(...)` | `DataSet` / `JSONArray` | 多个 `GenerateSqlTran` 的顺序执行结果。 |
| `queryTranJsonObject(...)` | `JSONObject` | 多个生成 SQL 的对象化结果。 |
| `queryCount/queryJsonCount/queryMapCount(...)` | `int` | 数量查询。 |
| `executeSql(...)` | `int` | 执行单条/多条 SQL 或 `GenerateSqlTran`，用于增删改。 |
| `executeSqlRes(...)` | `List<String>` | 执行多条 SQL 并返回结果信息。 |
| `executeSqlTran(...)` | `int` | 执行 `GenerateSqlTran` 批处理列表。 |
| `callProcedures(dbName, proceduresParameter)` | `void` | 调用存储过程，输出参数回写至 `DbParameter`。 |
| `keepSqlTran(...)` / `keepExecute(...)` | `Boolean` | 长事务执行与控制。 |

当传入 `cacheKey` 时，组件会使用 Redis 缓存查询结果；多租户模式下会自动加上 `<tenant>:` 前缀。写入数据后需要由业务方删除对应缓存键。

## 4. 方言与序列：`DbValue`

`DbValue` 根据数据源驱动识别 Oracle（`1`）、PostgreSQL（`3`）和达梦（`4`），生成相应 SQL 片段。

| 方法 | 用途 |
| --- | --- |
| `getSeqValue(dbName, table)` / `getListSeqVal(dbName, seq)` | 获取单个/批量主键序列值。 |
| `getSeq(dbName, table)` | 返回获取序列值的 SQL。 |
| `getSysDate*`、`getDateSql`、`getDateInterval` | 获取数据库当前时间或日期计算 SQL。 |
| `getRowNum`、`getRowVal`、`dual` | 生成行数限制、行号或单行查询方言片段。 |
| `convertVarchar`、`getTrim`、`getCeil`、`getFloor`、`getMod` | 常用表达式方言适配。 |
| `queryRestrictions(sql)` | 检查 SQL 中的危险关键字；返回 `SqlRestrictionsInfo`。 |

注意：MySQL（`0`）和 SQL Server（`2`）分支目前大多数方法没有实现，使用这些数据库前应补充方言实现并验证结果。

## 5. 多数据源与租户

| 类 | 公共 API | 用途 |
| --- | --- | --- |
| `MultiDataSourceHolder` | `setDatasource`、`getDatasource`、`clearDataSource` | 以 `ThreadLocal` 保存当前数据源/租户标识。必须在请求或异步任务结束时清理。 |
| `Tenant` | `single`、`tenanturl`、`systemType`、`defaultTenant`、`orgKey` | 绑定 `tenant.*` 配置。`single=true` 表示不拼接 tenant 数据源后缀。 |
| `InterceptTenant` | `getTenantHeader()` | 读取当前租户（Header `tenant` 或线程上下文）。 |
| `DbConfig` | `getType`、`getUserName`、`getHikariDataSource`、`checkTenant` | 获取数据源属性及验证租户数据源。 |

异步场景示例：

```java
MultiDataSourceHolder.setDatasource("tenant-a");
try {
    // 调用需要 tenant 上下文的数据库逻辑
} finally {
    MultiDataSourceHolder.clearDataSource();
}
```

## 6. Redis：`RedisService`

该 Bean 基于 `RedisTemplate`，提供对象、List、Set、Hash 操作。

| 类型 | 写入 | 读取/删除 |
| --- | --- | --- |
| 普通对象 | `setCacheObject(key, value[, timeout, unit])` | `getCacheObject`、`deleteObject`、`hasKey`、`expire` |
| List | `setCacheList` | `getCacheList` |
| Set | `setCacheSet` | `getCacheSet` |
| Hash | `setCacheMap`、`setCacheMapValue` | `getCacheMap`、`getCacheMapValue`、`getMultiCacheMapValue`、`deleteCacheMapValue` |

`keys(pattern)` 会在 Redis 中按模式查找键；生产环境应避免宽泛通配符，防止阻塞 Redis。

## 7. MinIO：`FileService`

| 方法 | 说明 |
| --- | --- |
| `upload(file, objectName)` | 上传至 `minio.bucket-name` 配置的默认桶；桶不存在时自动创建。 |
| `upload(file, objectName, bucketName)` | 上传至指定桶；桶不存在时自动创建。 |
| `download(objectName)` | 从默认桶获取对象流。调用方必须关闭返回的 `InputStream`。 |

上传成功不返回 URL。`objectName` 应由业务方按目录/文件名规则生成并避免冲突。

## 8. 其他公共能力

- `TenantHttpContext`：读取当前请求、响应、Session、请求参数及 Header，仅能在 HTTP 请求线程中使用。
- `JdbcDbHelp`：使用 `JdbcConnectModel` 连接外部 JDBC 数据库，提供 `connect`、`executeSql`、`queryJson`、`queryJsonObject`，调用方应避免在日志中输出 `pwd`。
- `TableXmlOperation`：读取、重置和清理表字段 XML 元数据缓存：`getTableXml`、`getTableXmlNode`、`resetTable`、`clearCacheByDb`、`clearAllCache`。
- `DESUtils`：`encryptByDES` / `decryptByDES`。仅用于兼容现有数据；新功能应采用更现代的带认证加密方案。
- `SpecialHandle`：`execute(tenant, task, args...)` 在指定租户上下文中运行自定义回调。

## 9. 配置要点

模块自身的 `application.yml` 仅保留示例且已注释。实际应用需要在服务配置中提供：

```yaml
tenant:
  single: true

minio:
  endpoint: http://minio.example.internal:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: wms
```

数据源由 `MultiDataSource` 管理；确保默认数据源和所有命名数据源均已配置。多租户模式还应保证 `<dbName>-<tenant>` 对应的数据源存在。

## 10. 使用约束

1. 生成 SQL 后先校验 `GenerateSqlTran.getRes()`；`getErrList()` 会将结果状态标记为失败。
2. 原始 SQL 执行 API 接受字符串，调用方必须使用参数化/受控字段和 `DbValue.queryRestrictions` 等校验，不能拼接用户输入。
3. `DbKeepTran` 的构造函数会直接新建 `DbHelp` 并打开连接，不建议在常规业务流中使用；优先使用标准 Spring 事务和 `DbHelp`。
4. `FileService.download` 返回的流由调用方关闭；`MultiDataSourceHolder` 必须在 finally 中清理，避免线程池上下文泄漏。
