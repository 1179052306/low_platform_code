package com.server.basedata.api;

/**
 * 紧凑行数据结构。
 * <p>
 * 以对象数组形式承载一行基础数据的字段值，避免 Map 的装箱开销和哈希查找成本，
 * 适配高频读取、内存敏感的缓存场景。除字段值数组外，还携带 schemaVersion
 * （结构版本）与 dataVersion（数据版本），用于多版本缓存隔离与一致性校验。
 * <p>
 * 该类为不可变值对象：字段全部 final，外部获取 values 数组时返回原始引用，
 * 调用方应避免修改数组内容。
 */
public final class CompactRow {

  /** 主键值 */
  private final Object id;
  /** 结构版本号，对应 Schema 的版本，用于多版本缓存隔离 */
  private final int schemaVersion;
  /** 数据版本号，对应行数据的版本，用于乐观并发与失效判定 */
  private final int dataVersion;
  /** 按投影顺序排列的字段值数组；null 表示空行 */
  private final Object[] values;

  /**
   * 构造一个紧凑行。
   *
   * @param id            主键值
   * @param values        按投影顺序排列的字段值数组，可为 null
   * @param schemaVersion 结构版本号
   * @param dataVersion   数据版本号
   */
  public CompactRow(Object id, Object[] values, int schemaVersion, int dataVersion) {
    this.id = id;
    this.values = values;
    this.schemaVersion = schemaVersion;
    this.dataVersion = dataVersion;
  }

  /**
   * 按下标获取字段值。
   * <p>
   * 当 values 为 null 或下标越界时返回 null，不抛异常，便于调用方容错处理。
   *
   * @param index 字段下标（从 0 开始）
   * @return 字段值；越界或空行时返回 null
   */
  public Object get(int index) {
    Object result;
    if (values == null) {
      // 空行，直接返回 null
      result = null;
    } else if (index < 0 || index >= values.length) {
      // 下标越界，返回 null 而非抛异常
      result = null;
    } else {
      result = values[index];
    }
    return result;
  }

  /**
   * 获取主键值。
   *
   * @return 主键值
   */
  public Object getId() {
    return id;
  }

  /**
   * 获取结构版本号。
   *
   * @return 结构版本号
   */
  public int getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * 获取数据版本号。
   *
   * @return 数据版本号
   */
  public int getDataVersion() {
    return dataVersion;
  }

  /**
   * 获取字段数量。
   * <p>
   * 当 values 为 null 时返回 0。
   *
   * @return 字段数量
   */
  public int size() {
    int result;
    if (values == null) {
      // 空行视为 0 个字段
      result = 0;
    } else {
      result = values.length;
    }
    return result;
  }

  /**
   * 获取原始字段值数组引用。
   * <p>
   * 返回的是内部数组的直接引用，调用方不应修改其内容，以保证不可变性。
   *
   * @return 字段值数组，可能为 null
   */
  public Object[] getValues() {
    return values;
  }
}
