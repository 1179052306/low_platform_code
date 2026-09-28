package com.server.basedata;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.basedata.api.BaseDataEngine;
import com.server.basedata.api.CacheMetrics;
import com.server.basedata.api.CompactRow;
import com.server.basedata.metadata.CompiledEntity;
import com.server.basedata.metadata.RuntimeMetadata;
import com.server.basedata.metadata.VersionManager;
import com.server.basedata.relation.RelationResolver;
import com.server.basedata.security.AdminTokenValidator;
import com.server.basedata.version.SchemaVersionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * V3 基础数据引擎 REST 接口 — 前端通过 HTTP 调用缓存查询/失效/管理。
 * <p>
 * 核心接口：
 * <ul>
 * <li>GET /api/basedata/get — 单条查询（L1→L2→DB）</li>
 * <li>POST /api/basedata/getBatch — 批量查询</li>
 * <li>POST /api/basedata/resolve — 关联解析（主实体 + N:1 关联实体）</li>
 * <li>DELETE /api/basedata/invalidate — 失效缓存</li>
 * <li>GET /api/basedata/metrics — 获取缓存指标</li>
 * <li>GET /api/basedata/entities — 获取所有实体元数据</li>
 * <li>POST /api/basedata/publish — 发布新元数据</li>
 * <li>POST /api/basedata/rollback — 回滚元数据</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/basedata")
public class BaseDataController {

  /** 批量查询 ids 最大数量，防止 DoS */
  private static final int MAX_BATCH_SIZE = 500;
  /** 关联解析最大深度，防止过度递归 */
  private static final int MAX_RESOLVE_DEPTH = 10;

  @Resource
  private BaseDataEngine engine;

  @Resource
  private VersionManager versionManager;

  @Resource
  private SchemaVersionManager schemaVersionManager;

  @Resource
  private RelationResolver relationResolver;

  @Resource
  private AdminTokenValidator adminTokenValidator;

  /**
   * 单条查询：按 cacheName + id 查询，支持 projection 裁剪。
   * <p>
   * 示例：GET /api/basedata/get?cacheName=B_ITEM&id=10001&projection=PDA
   * </p>
   *
   * @return CompactRow 的 JSON 表示（字段名→值），未命中返回空 JSON
   */
  @GetMapping("/get")
  public JSONObject get(
      @RequestParam String cacheName,
      @RequestParam String id,
      @RequestParam(required = false) String projection) {
    JSONObject result = new JSONObject();
    try {
      CompactRow row = engine.get(cacheName, id, projection);
      result = rowToJson(row, cacheName);
    } catch (Exception e) {
      log.error("查询失败: cacheName={}, id={}", cacheName, id, e);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 批量查询：按 cacheName + ids 批量查询，支持 projection 裁剪。
   * <p>
   * 请求体示例：
   * </p>
   * 
   * <pre>
   * {"cacheName":"B_ITEM","ids":[10001,10002,10003],"projection":"PDA"}
   * </pre>
   *
   * @return JSONObject，包含 data（行数组）和可选的 error 字段
   */
  @PostMapping("/getBatch")
  public JSONObject getBatch(@RequestBody JSONObject req) {
    JSONObject result = new JSONObject();
    try {
      String cacheName = req.getString("cacheName");
      JSONArray idsArray = req.getJSONArray("ids");
      String projection = req.getString("projection");

      // 批量大小限制，防止 DoS
      if (idsArray != null && idsArray.size() > MAX_BATCH_SIZE) {
        result.put("error", "批量查询数量超过上限 " + MAX_BATCH_SIZE);
        return result;
      }

      List<Object> ids = new ArrayList<Object>();
      for (int i = 0; i < idsArray.size(); i++) {
        ids.add(idsArray.get(i));
      }

      Map<Object, CompactRow> rows = engine.getBatch(cacheName, ids, projection);
      JSONArray data = new JSONArray();
      for (Map.Entry<Object, CompactRow> entry : rows.entrySet()) {
        data.add(rowToJson(entry.getValue(), cacheName));
      }
      result.put("data", data);
    } catch (Exception e) {
      log.error("批量查询失败: {}", req, e);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 关联解析：查询主实体 + 自动加载 N:1 关联实体。
   * <p>
   * 请求体示例：
   * </p>
   * 
   * <pre>
   * {"cacheName":"B_ITEM","ids":[10001,10002],"projection":"PDA","maxDepth":3}
   * </pre>
   *
   * @return JSONObject，包含 main（主实体数组）和 related（关联实体映射：cacheName → 行数组）
   */
  @PostMapping("/resolve")
  public JSONObject resolve(@RequestBody JSONObject req) {
    JSONObject result = new JSONObject();
    try {
      String cacheName = req.getString("cacheName");
      JSONArray idsArray = req.getJSONArray("ids");
      String projection = req.getString("projection");
      int maxDepth = req.getIntValue("maxDepth", 5);

      // 批量大小限制，防止 DoS
      if (idsArray != null && idsArray.size() > MAX_BATCH_SIZE) {
        result.put("error", "关联解析数量超过上限 " + MAX_BATCH_SIZE);
        return result;
      }
      // 深度限制，防止过度递归
      if (maxDepth > MAX_RESOLVE_DEPTH) {
        maxDepth = MAX_RESOLVE_DEPTH;
      }

      List<Object> ids = new ArrayList<Object>();
      for (int i = 0; i < idsArray.size(); i++) {
        ids.add(idsArray.get(i));
      }

      // 1. 查主实体（应用 projection 裁剪）
      Map<Object, CompactRow> mainRows = engine.getBatch(cacheName, ids, projection);
      List<CompactRow> sourceData = new ArrayList<CompactRow>(mainRows.values());

      // 2. 关联解析（按层批量加载 N:1 关联），收集所有关联行
      Map<String, List<CompactRow>> relatedData = new LinkedHashMap<String, List<CompactRow>>();
      RuntimeMetadata metadata = versionManager.getCurrent();
      if (metadata != null && !sourceData.isEmpty()) {
        relatedData = relationResolver.resolve(sourceData, cacheName, metadata, maxDepth);
      }

      // 3. 主实体转为 JSON 数组
      JSONArray mainArray = new JSONArray();
      for (CompactRow row : sourceData) {
        mainArray.add(rowToJson(row, cacheName));
      }
      result.put("main", mainArray);

      // 4. 关联实体转为 JSON 映射（cacheName → 行数组）
      JSONObject relatedObj = new JSONObject();
      for (Map.Entry<String, List<CompactRow>> entry : relatedData.entrySet()) {
        JSONArray relArray = new JSONArray();
        for (CompactRow row : entry.getValue()) {
          relArray.add(rowToJson(row, entry.getKey()));
        }
        relatedObj.put(entry.getKey(), relArray);
      }
      result.put("related", relatedObj);
    } catch (Exception e) {
      log.error("关联解析失败: {}", req, e);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 失效单条缓存：删除 L1 + L2 中对应 Key。
   * <p>
   * 示例：DELETE /api/basedata/invalidate?cacheName=B_ITEM&id=10001
   * </p>
   */
  @DeleteMapping("/invalidate")
  public JSONObject invalidate(
      @RequestParam String cacheName,
      @RequestParam String id) {
    JSONObject result = new JSONObject();
    try {
      engine.invalidate(cacheName, id);
      result.put("success", true);
    } catch (Exception e) {
      log.error("失效失败: cacheName={}, id={}", cacheName, id, e);
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 批量失效缓存。
   * <p>
   * 请求体示例：
   * </p>
   * 
   * <pre>
   * {"cacheName":"B_ITEM","ids":[10001,10002]}
   * </pre>
   */
  @PostMapping("/invalidateBatch")
  public JSONObject invalidateBatch(@RequestBody JSONObject req) {
    JSONObject result = new JSONObject();
    try {
      String cacheName = req.getString("cacheName");
      JSONArray idsArray = req.getJSONArray("ids");
      List<Object> ids = new ArrayList<Object>();
      for (int i = 0; i < idsArray.size(); i++) {
        ids.add(idsArray.get(i));
      }
      engine.invalidateBatch(cacheName, ids);
      result.put("success", true);
      result.put("count", ids.size());
    } catch (Exception e) {
      log.error("批量失效失败: {}", req, e);
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 清空指定缓存空间的全部缓存（L1 + L2 + 空值缓存）。
   * <p>
   * 示例：POST /api/basedata/invalidateAll?cacheName=B_ITEM
   * </p>
   * <p>
   * 需要 X-Admin-Token 请求头授权。
   * </p>
   */
  @PostMapping("/invalidateAll")
  public JSONObject invalidateAll(
      @RequestParam String cacheName,
      @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
    JSONObject result = new JSONObject();
    if (!adminTokenValidator.validate(adminToken)) {
      result.put("success", false);
      result.put("error", "未授权：无效或缺失的 X-Admin-Token");
      return result;
    }
    try {
      engine.invalidateAll(cacheName);
      result.put("success", true);
    } catch (Exception e) {
      log.error("清空缓存失败: cacheName={}", cacheName, e);
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 获取缓存指标：命中率、回源次数等。
   * <p>
   * 示例：GET /api/basedata/metrics?cacheName=B_ITEM
   * </p>
   */
  @GetMapping("/metrics")
  public JSONObject metrics(@RequestParam String cacheName) {
    JSONObject result = new JSONObject();
    try {
      CacheMetrics metrics = engine.getMetrics(cacheName);
      result.put("cacheName", cacheName);
      result.put("l1HitCount", metrics.getL1HitCount());
      result.put("l1MissCount", metrics.getL1MissCount());
      result.put("l1HitRate", metrics.getL1HitRate());
      result.put("l2HitCount", metrics.getL2HitCount());
      result.put("l2MissCount", metrics.getL2MissCount());
      result.put("l2HitRate", metrics.getL2HitRate());
      result.put("dbLoadCount", metrics.getDbLoadCount());
      result.put("avgDbLoadMillis", metrics.getAvgDbLoadMillis());
      result.put("nullCacheCount", metrics.getNullCacheCount());
      result.put("invalidateCount", metrics.getInvalidateCount());
    } catch (Exception e) {
      log.error("获取指标失败: cacheName={}", cacheName, e);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 获取所有实体元数据列表（前端管理界面用）。
   *
   * @return JSONObject，包含 data（实体数组）和可选的 error 字段
   */
  @GetMapping("/entities")
  public JSONObject entities() {
    JSONObject result = new JSONObject();
    try {
      JSONArray data = new JSONArray();
      RuntimeMetadata metadata = versionManager.getCurrent();
      if (metadata == null) {
        result.put("data", data);
        return result;
      }
      for (String cacheName : metadata.getAllCacheNames()) {
        CompiledEntity entity = metadata.getEntity(cacheName);
        JSONObject obj = new JSONObject();
        obj.put("cacheName", entity.getCacheName());
        obj.put("tableName", entity.getTableName());
        obj.put("idColumn", entity.getIdColumn());
        obj.put("codeColumn", entity.getCodeColumn());
        obj.put("schemaVersion", entity.getSchemaVersion());
        obj.put("fieldCount", entity.getFieldCount());
        obj.put("ttl", entity.getTtl());
        obj.put("maxCapacity", entity.getMaxCapacity());

        // 关联关系
        List<com.server.basedata.metadata.CompiledRelation> relations = metadata.getRelations(cacheName);
        JSONArray relArray = new JSONArray();
        for (com.server.basedata.metadata.CompiledRelation rel : relations) {
          JSONObject relObj = new JSONObject();
          relObj.put("targetCache", rel.getTargetCache());
          relObj.put("targetColumn", rel.getTargetColumn());
          relObj.put("relationType", rel.getRelationType());
          relArray.add(relObj);
        }
        obj.put("relations", relArray);

        data.add(obj);
      }
      result.put("data", data);
    } catch (Exception e) {
      log.error("获取实体列表失败", e);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 发布新元数据：从数据库重新加载元数据 → 编译 → 原子切换。
   * <p>
   * 需要 X-Admin-Token 请求头授权。
   * </p>
   *
   * @return {"success":true,"version":2} 或 {"success":false,"error":"..."}
   */
  @PostMapping("/publish")
  public JSONObject publish(
      @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
    JSONObject result = new JSONObject();
    if (!adminTokenValidator.validate(adminToken)) {
      result.put("success", false);
      result.put("error", "未授权：无效或缺失的 X-Admin-Token");
      return result;
    }
    try {
      boolean success = schemaVersionManager.publishNewMetadata();
      result.put("success", success);
      result.put("version", schemaVersionManager.getCurrentVersion());
    } catch (Exception e) {
      log.error("发布元数据失败", e);
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 回滚元数据：恢复到上一个版本。
   * <p>
   * 需要 X-Admin-Token 请求头授权。
   * </p>
   *
   * @return {"success":true,"version":1} 或 {"success":false,"error":"..."}
   */
  @PostMapping("/rollback")
  public JSONObject rollback(
      @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
    JSONObject result = new JSONObject();
    if (!adminTokenValidator.validate(adminToken)) {
      result.put("success", false);
      result.put("error", "未授权：无效或缺失的 X-Admin-Token");
      return result;
    }
    try {
      boolean success = schemaVersionManager.rollback();
      result.put("success", success);
      result.put("version", schemaVersionManager.getCurrentVersion());
    } catch (Exception e) {
      log.error("回滚元数据失败", e);
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 将 CompactRow 转为 JSON 表示（字段名→值）。
   * 需要从 RuntimeMetadata 获取字段名列表。
   */
  private JSONObject rowToJson(CompactRow row, String cacheName) {
    JSONObject result = new JSONObject();
    if (row == null) {
      return result;
    }

    result.put("_ID", row.getId());
    result.put("_SCHEMA_VERSION", row.getSchemaVersion());

    RuntimeMetadata metadata = versionManager.getCurrent();
    if (metadata == null) {
      return result;
    }

    CompiledEntity entity = metadata.getEntity(cacheName);
    if (entity == null) {
      return result;
    }

    String[] fieldNames = entity.getFieldNames();
    if (fieldNames == null) {
      return result;
    }

    for (int i = 0; i < fieldNames.length; i++) {
      result.put(fieldNames[i], row.get(i));
    }
    return result;
  }
}