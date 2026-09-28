package com.server.basedata.relation;

import com.server.basedata.api.BaseDataEngine;
import com.server.basedata.api.CompactRow;
import com.server.basedata.metadata.CompiledEntity;
import com.server.basedata.metadata.CompiledRelation;
import com.server.basedata.metadata.RuntimeMetadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关联解析器。
 * <p>
 * 从一批源数据出发，按元数据中定义的关联关系递归展开目标实体：从源行的关联字段提取目标主键，
 * 批量回源加载目标行，再对目标行继续展开，直到达到最大深度或无更多关联。通过 visited 集合
 * 避免循环展开同一缓存空间。
 */
public class RelationResolver {

  /** 基础数据引擎，用于批量加载目标行 */
  private final BaseDataEngine engine;

  /**
   * 构造解析器。
   *
   * @param engine 基础数据引擎
   */
  public RelationResolver(BaseDataEngine engine) {
    this.engine = engine;
  }

  /**
   * 从源数据出发解析关联，递归展开到 maxDepth 层，收集所有加载的关联行。
   * <p>
   * 将起始缓存空间记入 visited，避免后续重复展开。返回的 Map 以目标缓存空间名为 key，
   * 该空间下加载到的全部关联行为 value，保持插入顺序便于前端按展开层级展示。
   *
   * @param sourceData 源数据列表
   * @param cacheName  起始缓存空间名
   * @param metadata   运行时元数据
   * @param maxDepth   最大展开深度
   * @return 所有关联缓存空间 → 关联行列表（不含主实体本身）
   */
  public Map<String, List<CompactRow>> resolve(List<CompactRow> sourceData, String cacheName,
      RuntimeMetadata metadata, int maxDepth) {
    Set<String> visited = new HashSet<String>();
    visited.add(cacheName);
    // 用 LinkedHashMap 保持展开顺序，便于前端按层级展示关联结果
    Map<String, List<CompactRow>> allRelated = new LinkedHashMap<String, List<CompactRow>>();
    resolveInternal(sourceData, cacheName, metadata, maxDepth, 0, visited, allRelated);
    return allRelated;
  }

  /**
   * 递归展开关联的内部实现。
   * <p>
   * 终止条件：达到最大深度、源数据为空、当前实体不存在、无关联定义。
   * 对每条关联：跳过已访问的目标缓存，从源行提取目标主键，批量加载目标行，
   * 将目标行收集到 allRelated 中，再递归展开下一层。
   *
   * @param sourceData   当前层源数据
   * @param currentCache 当前缓存空间名
   * @param metadata     运行时元数据
   * @param maxDepth     最大深度
   * @param currentDepth 当前深度
   * @param visited      已访问缓存空间集合
   * @param allRelated   收集所有关联行的结果 Map（按 cacheName 分组）
   */
  private void resolveInternal(List<CompactRow> sourceData, String currentCache,
      RuntimeMetadata metadata, int maxDepth, int currentDepth,
      Set<String> visited, Map<String, List<CompactRow>> allRelated) {
    if (currentDepth >= maxDepth) {
      // 达到最大深度，停止展开
      return;
    }
    if (sourceData == null || sourceData.isEmpty()) {
      // 无源数据，停止
      return;
    }

    CompiledEntity entity = metadata.getEntity(currentCache);
    if (entity == null) {
      // 实体不存在，停止
      return;
    }

    List<CompiledRelation> relations = metadata.getRelations(currentCache);
    if (relations == null || relations.isEmpty()) {
      // 无关联定义，停止
      return;
    }

    for (CompiledRelation rel : relations) {
      String targetCache = rel.getTargetCache();
      if (visited.contains(targetCache)) {
        // 已访问过该目标缓存，跳过避免循环
        continue;
      }
      visited.add(targetCache);

      // 从源行的关联字段提取目标主键
      Set<Object> targetIds = extractIds(sourceData, rel);
      if (targetIds.isEmpty()) {
        continue;
      }

      // 批量加载目标行
      Map<Object, CompactRow> targetRows = engine.getBatch(targetCache, targetIds);
      List<CompactRow> targetList = new ArrayList<CompactRow>(targetRows.values());

      if (!targetList.isEmpty()) {
        // 收集关联行到结果 Map，供 Controller 返回给前端
        allRelated.put(targetCache, targetList);
        // 递归展开下一层
        resolveInternal(targetList, targetCache, metadata, maxDepth,
            currentDepth + 1, visited, allRelated);
      }
    }
  }

  /**
   * 从源数据中按关联字段下标提取目标主键集合。
   *
   * @param sourceData 源数据列表
   * @param relation   关联定义
   * @return 目标主键集合（已去 null）
   */
  private Set<Object> extractIds(List<CompactRow> sourceData, CompiledRelation relation) {
    Set<Object> result = new HashSet<Object>();
    int fieldIndex = relation.getSourceFieldIndex();
    for (CompactRow row : sourceData) {
      Object id = row.get(fieldIndex);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }
}
