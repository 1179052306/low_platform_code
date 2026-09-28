package com.server.basedata.metadata;

/**
 * 预编译投影。
 * <p>
 * 将投影表达式预编译为字段下标数组，运行时按这些下标从 {@link CompactRow} 中提取子集，
 * 避免每次查询都解析投影字符串。投影与具体实体绑定，下标基于该实体的字段顺序。
 */
public final class CompiledProjection {

  /** 所属缓存空间名 */
  private final String cacheName;
  /** 投影名 */
  private final String projectionName;
  /** 投影包含的字段下标数组，顺序对应投影定义 */
  private final int[] fieldIndexes;

  /**
   * 构造预编译投影。
   *
   * @param cacheName      缓存空间名
   * @param projectionName 投影名
   * @param fieldIndexes   字段下标数组
   */
  public CompiledProjection(String cacheName, String projectionName, int[] fieldIndexes) {
    this.cacheName = cacheName;
    this.projectionName = projectionName;
    this.fieldIndexes = fieldIndexes;
  }

  /**
   * 获取缓存空间名。
   *
   * @return 缓存空间名
   */
  public String getCacheName() {
    return cacheName;
  }

  /**
   * 获取投影名。
   *
   * @return 投影名
   */
  public String getProjectionName() {
    return projectionName;
  }

  /**
   * 获取字段下标数组。
   *
   * @return 字段下标数组
   */
  public int[] getFieldIndexes() {
    return fieldIndexes;
  }

  /**
   * 获取投影字段数量。
   *
   * @return 字段数量；下标数组为 null 时返回 0
   */
  public int getFieldCount() {
    int result;
    if (fieldIndexes == null) {
      result = 0;
    } else {
      result = fieldIndexes.length;
    }
    return result;
  }
}
