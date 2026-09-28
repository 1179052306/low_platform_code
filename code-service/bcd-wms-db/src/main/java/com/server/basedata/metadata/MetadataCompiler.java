package com.server.basedata.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据编译器。
 * <p>
 * 将 {@link MetadataLoader.RawMetadata} 中的原始行编译为 {@link RuntimeMetadata}：
 * 把字段名解析为下标、投影字段名解析为下标数组、关联源列名解析为下标，
 * 并按缓存空间分组组织。编译后产物为不可变快照，可直接发布到 {@link VersionManager}。
 * <p>
 * 编译过程中会对字段名/列名做存在性校验，引用了不存在字段的投影或关联会被跳过（返回 null）。
 */
public class MetadataCompiler {

  /**
   * 编译原始元数据为运行时元数据。
   *
   * @param raw     原始元数据
   * @param version 版本号
   * @return 编译后的运行时元数据
   */
  public RuntimeMetadata compile(MetadataLoader.RawMetadata raw, long version) {
    Map<String, CompiledEntity> entityMap = buildEntities(raw);
    Map<String, Map<String, CompiledProjection>> projectionMap = buildProjections(raw, entityMap);
    Map<String, List<CompiledRelation>> relationMap = buildRelations(raw, entityMap);
    return new RuntimeMetadata(version, entityMap, projectionMap, relationMap);
  }

  /**
   * 编译全部实体。
   * <p>
   * 先按缓存空间分组字段，再逐实体编译。
   *
   * @param raw 原始元数据
   * @return 缓存空间名 → 预编译实体
   */
  private Map<String, CompiledEntity> buildEntities(MetadataLoader.RawMetadata raw) {
    Map<String, CompiledEntity> result = new HashMap<String, CompiledEntity>();

    Map<String, List<MetadataLoader.FieldRow>> fieldsByCache = groupFieldsByCache(raw.fields);

    for (MetadataLoader.EntityRow entity : raw.entities) {
      List<MetadataLoader.FieldRow> fieldRows = fieldsByCache.get(entity.cacheName);
      CompiledEntity compiled = compileEntity(entity, fieldRows);
      result.put(entity.cacheName, compiled);
    }
    return result;
  }

  /**
   * 编译单个实体：将字段行转换为字段名数组、加密标记数组与 TTL 数组。
   * <p>
   * 字段独立 TTL 为 null 时回退到实体默认 TTL。
   *
   * @param entity    实体行
   * @param fieldRows 字段行列表
   * @return 预编译实体
   */
  private CompiledEntity compileEntity(MetadataLoader.EntityRow entity,
      List<MetadataLoader.FieldRow> fieldRows) {
    String[] fieldNames = null;
    boolean[] encryptedFlags = null;
    int[] fieldTtls = null;

    if (fieldRows != null && !fieldRows.isEmpty()) {
      int count = fieldRows.size();
      fieldNames = new String[count];
      encryptedFlags = new boolean[count];
      fieldTtls = new int[count];
      for (int i = 0; i < count; i++) {
        MetadataLoader.FieldRow fr = fieldRows.get(i);
        fieldNames[i] = fr.columnName;
        encryptedFlags[i] = fr.isEncrypted;
        int ttl;
        if (fr.fieldTtl != null) {
          // 字段独立 TTL 优先
          ttl = fr.fieldTtl.intValue();
        } else {
          // 回退到实体默认 TTL
          ttl = entity.ttl;
        }
        fieldTtls[i] = ttl;
      }
    }

    return new CompiledEntity(
        entity.cacheName,
        entity.tableName,
        entity.idColumn,
        entity.codeColumn,
        fieldNames,
        entity.schemaVersion,
        entity.schemaHash,
        encryptedFlags,
        fieldTtls,
        entity.ttl,
        entity.maxCapacity,
        true);
  }

  /**
   * 编译全部投影，按缓存空间分组。
   *
   * @param raw       原始元数据
   * @param entityMap 已编译实体映射（用于解析字段下标）
   * @return 缓存空间名 → (投影名 → 预编译投影)
   */
  private Map<String, Map<String, CompiledProjection>> buildProjections(
      MetadataLoader.RawMetadata raw,
      Map<String, CompiledEntity> entityMap) {
    Map<String, Map<String, CompiledProjection>> result = new HashMap<String, Map<String, CompiledProjection>>();

    for (MetadataLoader.ProjectionRow proj : raw.projections) {
      CompiledEntity entity = entityMap.get(proj.cacheName);
      CompiledProjection compiled = compileProjection(proj, entity);
      // 编译失败的投影（引用不存在字段）跳过
      if (compiled != null) {
        Map<String, CompiledProjection> inner = result.get(proj.cacheName);
        if (inner == null) {
          inner = new HashMap<String, CompiledProjection>();
          result.put(proj.cacheName, inner);
        }
        inner.put(proj.projectionName, compiled);
      }
    }
    return result;
  }

  /**
   * 编译单个投影：将投影字段名解析为下标数组。
   * <p>
   * 任一字段名在实体中不存在时返回 null，表示投影无效。
   *
   * @param proj   投影行
   * @param entity 所属实体
   * @return 预编译投影；无效时返回 null
   */
  private CompiledProjection compileProjection(MetadataLoader.ProjectionRow proj,
      CompiledEntity entity) {
    CompiledProjection result;
    if (entity == null || proj.fields == null || proj.fields.isEmpty()) {
      // 实体不存在或投影字段为空，视为无效
      result = null;
    } else {
      int[] indexes = new int[proj.fields.size()];
      boolean valid = true;
      for (int i = 0; i < proj.fields.size(); i++) {
        int idx = entity.getFieldIndex(proj.fields.get(i));
        if (idx < 0) {
          // 投影引用了不存在的字段，标记无效
          valid = false;
          break;
        }
        indexes[i] = idx;
      }
      if (valid) {
        result = new CompiledProjection(proj.cacheName, proj.projectionName, indexes);
      } else {
        result = null;
      }
    }
    return result;
  }

  /**
   * 编译全部关联，按源缓存空间分组。
   *
   * @param raw       原始元数据
   * @param entityMap 已编译实体映射
   * @return 源缓存空间名 → 关联列表
   */
  private Map<String, List<CompiledRelation>> buildRelations(
      MetadataLoader.RawMetadata raw,
      Map<String, CompiledEntity> entityMap) {
    Map<String, List<CompiledRelation>> result = new HashMap<String, List<CompiledRelation>>();

    for (MetadataLoader.RelationRow rel : raw.relations) {
      CompiledEntity sourceEntity = entityMap.get(rel.sourceCache);
      CompiledRelation compiled = compileRelation(rel, sourceEntity);
      // 编译失败的关联（源实体或源列不存在）跳过
      if (compiled != null) {
        List<CompiledRelation> list = result.get(rel.sourceCache);
        if (list == null) {
          list = new ArrayList<CompiledRelation>();
          result.put(rel.sourceCache, list);
        }
        list.add(compiled);
      }
    }
    return result;
  }

  /**
   * 编译单个关联：将源列名解析为字段下标。
   * <p>
   * 源实体或源列不存在时返回 null。
   *
   * @param rel          关联行
   * @param sourceEntity 源实体
   * @return 预编译关联；无效时返回 null
   */
  private CompiledRelation compileRelation(MetadataLoader.RelationRow rel,
      CompiledEntity sourceEntity) {
    CompiledRelation result;
    if (sourceEntity == null) {
      // 源实体不存在
      result = null;
    } else {
      int sourceFieldIndex = sourceEntity.getFieldIndex(rel.sourceColumn);
      if (sourceFieldIndex < 0) {
        // 源列在实体中不存在
        result = null;
      } else {
        result = new CompiledRelation(
            rel.sourceCache,
            sourceFieldIndex,
            rel.targetCache,
            rel.targetColumn,
            0,
            rel.relationType);
      }
    }
    return result;
  }

  /**
   * 将字段行按缓存空间分组。
   *
   * @param fields 字段行列表
   * @return 缓存空间名 → 字段行列表
   */
  private Map<String, List<MetadataLoader.FieldRow>> groupFieldsByCache(
      List<MetadataLoader.FieldRow> fields) {
    Map<String, List<MetadataLoader.FieldRow>> result = new HashMap<String, List<MetadataLoader.FieldRow>>();
    for (MetadataLoader.FieldRow field : fields) {
      List<MetadataLoader.FieldRow> list = result.get(field.cacheName);
      if (list == null) {
        list = new ArrayList<MetadataLoader.FieldRow>();
        result.put(field.cacheName, list);
      }
      list.add(field);
    }
    return result;
  }
}
