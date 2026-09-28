package com.server.basedata.relation;

import com.server.basedata.metadata.CompiledRelation;
import com.server.basedata.metadata.RuntimeMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关联图。
 * <p>
 * 将 {@link RuntimeMetadata} 中的关联关系建模为有向图：节点为缓存空间名，边表示存在
 * 从源缓存到目标缓存的关联。提供邻接查询、边判断、节点集合等图操作，供
 * {@link CycleDetector} 做循环检测、{@link RelationResolver} 做关联展开规划使用。
 */
public class RelationGraph {

  /** 邻接表：源缓存 → 目标缓存列表（去重） */
  private final Map<String, List<String>> adjacencyMap;

  /**
   * 根据运行时元数据构建关联图。
   *
   * @param metadata 运行时元数据
   */
  public RelationGraph(RuntimeMetadata metadata) {
    this.adjacencyMap = buildAdjacencyMap(metadata);
  }

  /**
   * 构建邻接表。
   * <p>
   * 遍历每个缓存空间的关联列表，收集目标缓存并去重。
   *
   * @param metadata 运行时元数据
   * @return 邻接表
   */
  private Map<String, List<String>> buildAdjacencyMap(RuntimeMetadata metadata) {
    Map<String, List<String>> result = new HashMap<String, List<String>>();
    Set<String> cacheNames = metadata.getAllCacheNames();
    for (String cacheName : cacheNames) {
      List<CompiledRelation> relations = metadata.getRelations(cacheName);
      List<String> targets = new ArrayList<String>();
      for (CompiledRelation rel : relations) {
        // 同一目标缓存只保留一条边
        if (!targets.contains(rel.getTargetCache())) {
          targets.add(rel.getTargetCache());
        }
      }
      result.put(cacheName, targets);
    }
    return result;
  }

  /**
   * 获取指定节点的邻接目标列表。
   *
   * @param cacheName 源缓存空间名
   * @return 目标缓存列表；无邻接时返回空列表
   */
  public List<String> getAdjacent(String cacheName) {
    List<String> result = adjacencyMap.get(cacheName);
    if (result == null) {
      // 无邻接返回空列表，避免调用方判空
      result = new ArrayList<String>();
    }
    return result;
  }

  /**
   * 判断是否存在从 source 到 target 的边。
   *
   * @param source 源缓存空间名
   * @param target 目标缓存空间名
   * @return 存在边返回 true
   */
  public boolean hasEdge(String source, String target) {
    List<String> adjacent = adjacencyMap.get(source);
    boolean result;
    if (adjacent == null) {
      result = false;
    } else {
      result = adjacent.contains(target);
    }
    return result;
  }

  /**
   * 获取图中全部节点（含源与目标）。
   *
   * @return 节点集合
   */
  public Set<String> getAllNodes() {
    Set<String> result = new HashSet<String>();
    for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
      result.add(entry.getKey());
      result.addAll(entry.getValue());
    }
    return result;
  }
}
