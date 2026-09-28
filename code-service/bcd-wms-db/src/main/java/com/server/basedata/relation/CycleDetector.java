package com.server.basedata.relation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环检测器。
 * <p>
 * 基于 DFS 三色标记法检测关联图中是否存在环：WHITE（未访问）、GRAY（访问中，在当前递归栈上）、
 * BLACK（已完成）。若在 DFS 中遇到 GRAY 节点则存在环。另提供 {@link #wouldCreateCycle}
 * 判断新增一条边是否会引入环。
 */
public class CycleDetector {

  /**
   * DFS 节点颜色标记。
   */
  private enum Color {
    /** 未访问 */
    WHITE,
    /** 访问中（在当前递归栈） */
    GRAY,
    /** 已完成 */
    BLACK
  }

  /**
   * 检测图中是否存在环。
   * <p>
   * 对所有 WHITE 节点启动 DFS，遇到 GRAY 邻居即判定有环。
   *
   * @param graph 关联图
   * @return 存在环返回 true
   */
  public boolean hasCycle(RelationGraph graph) {
    boolean result = false;
    Map<String, Color> colors = new HashMap<String, Color>();
    // 初始化所有节点为 WHITE
    for (String node : graph.getAllNodes()) {
      colors.put(node, Color.WHITE);
    }
    for (String node : graph.getAllNodes()) {
      Color color = colors.get(node);
      if (color == Color.WHITE) {
        if (dfsVisit(graph, node, colors)) {
          result = true;
          break;
        }
      }
    }
    return result;
  }

  /**
   * DFS 访问单个节点，检测环。
   * <p>
   * 将节点标记为 GRAY，遍历邻居：遇到 GRAY 表示回边即有环；遇到 WHITE 则递归访问。
   * 访问完成后标记为 BLACK。
   *
   * @param graph  关联图
   * @param node   当前节点
   * @param colors 颜色映射
   * @return 发现环返回 true
   */
  private boolean dfsVisit(RelationGraph graph, String node, Map<String, Color> colors) {
    boolean result = false;
    colors.put(node, Color.GRAY);
    List<String> adjacent = graph.getAdjacent(node);
    for (String neighbor : adjacent) {
      Color neighborColor = colors.get(neighbor);
      if (neighborColor == null) {
        // 邻居未初始化，视为 WHITE
        colors.put(neighbor, Color.WHITE);
        neighborColor = Color.WHITE;
      }
      if (neighborColor == Color.GRAY) {
        // 回边，发现环
        result = true;
        break;
      }
      if (neighborColor == Color.WHITE) {
        // 递归访问未访问邻居
        if (dfsVisit(graph, neighbor, colors)) {
          result = true;
          break;
        }
      }
    }
    colors.put(node, Color.BLACK);
    return result;
  }

  /**
   * 判断新增一条 source→target 的边是否会引入环。
   * <p>
   * 若 source 与 target 相同（自环）或从 target 可达 source，则引入环。
   *
   * @param graph  关联图
   * @param source 源节点
   * @param target 目标节点
   * @return 会引入环返回 true
   */
  public boolean wouldCreateCycle(RelationGraph graph, String source, String target) {
    boolean result;
    if (source.equals(target)) {
      // 自环
      result = true;
    } else {
      // 若 target 已能到达 source，再加 source→target 即成环
      result = canReach(graph, target, source);
    }
    return result;
  }

  /**
   * 判断从 from 是否可达 to。
   *
   * @param graph 关联图
   * @param from  起始节点
   * @param to    目标节点
   * @return 可达返回 true
   */
  private boolean canReach(RelationGraph graph, String from, String to) {
    boolean result = false;
    Map<String, Boolean> visited = new HashMap<String, Boolean>();
    result = dfsReach(graph, from, to, visited);
    return result;
  }

  /**
   * DFS 可达性判断。
   *
   * @param graph   关联图
   * @param current 当前节点
   * @param target  目标节点
   * @param visited 已访问标记
   * @return 可达返回 true
   */
  private boolean dfsReach(RelationGraph graph, String current, String target,
      Map<String, Boolean> visited) {
    boolean result = false;
    visited.put(current, true);
    List<String> adjacent = graph.getAdjacent(current);
    for (String neighbor : adjacent) {
      if (neighbor.equals(target)) {
        // 直接邻居即目标，可达
        result = true;
        break;
      }
      Boolean visitedNeighbor = visited.get(neighbor);
      if (visitedNeighbor == null || !visitedNeighbor.booleanValue()) {
        // 递归探索未访问邻居
        if (dfsReach(graph, neighbor, target, visited)) {
          result = true;
          break;
        }
      }
    }
    return result;
  }
}
