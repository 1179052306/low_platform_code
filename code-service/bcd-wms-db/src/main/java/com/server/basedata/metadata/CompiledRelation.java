package com.server.basedata.metadata;

/**
 * 预编译关联。
 * <p>
 * 描述从一个缓存空间（源）到另一个缓存空间（目标）的关联关系：源实体的某个字段
 * 持有目标实体的主键值，运行时可据此批量解析关联行。预编译后将源字段名解析为下标，
 * 避免运行时查找。
 */
public final class CompiledRelation {

  /** 源缓存空间名 */
  private final String sourceCache;
  /** 源字段下标（该字段值作为目标实体的查询键） */
  private final int sourceFieldIndex;
  /** 目标缓存空间名 */
  private final String targetCache;
  /** 目标列名（用于按值查询目标实体） */
  private final String targetColumn;
  /** 关联深度（用于多层关联展开） */
  private final int depth;
  /** 关联类型（如 one-to-one、one-to-many 等） */
  private final String relationType;

  /**
   * 构造预编译关联。
   *
   * @param sourceCache      源缓存空间名
   * @param sourceFieldIndex 源字段下标
   * @param targetCache      目标缓存空间名
   * @param targetColumn     目标列名
   * @param depth            关联深度
   * @param relationType     关联类型
   */
  public CompiledRelation(String sourceCache, int sourceFieldIndex,
      String targetCache, String targetColumn, int depth, String relationType) {
    this.sourceCache = sourceCache;
    this.sourceFieldIndex = sourceFieldIndex;
    this.targetCache = targetCache;
    this.targetColumn = targetColumn;
    this.depth = depth;
    this.relationType = relationType;
  }

  /**
   * 获取源缓存空间名。
   *
   * @return 源缓存空间名
   */
  public String getSourceCache() {
    return sourceCache;
  }

  /**
   * 获取源字段下标。
   *
   * @return 源字段下标
   */
  public int getSourceFieldIndex() {
    return sourceFieldIndex;
  }

  /**
   * 获取目标缓存空间名。
   *
   * @return 目标缓存空间名
   */
  public String getTargetCache() {
    return targetCache;
  }

  /**
   * 获取目标列名。
   *
   * @return 目标列名
   */
  public String getTargetColumn() {
    return targetColumn;
  }

  /**
   * 获取关联深度。
   *
   * @return 关联深度
   */
  public int getDepth() {
    return depth;
  }

  /**
   * 获取关联类型。
   *
   * @return 关联类型
   */
  public String getRelationType() {
    return relationType;
  }
}
