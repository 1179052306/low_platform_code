package com.server.basedata.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema 差异计算器。
 * <p>
 * 对比新旧两组列结构信息，识别出新增列、删除列、类型变更、长度变更、可空性变更等差异，
 * 供元数据变更检测与告警使用。差异以 {@link Change} 列表形式返回，每项描述一处变更。
 */
public class SchemaDiff {

  /**
   * 变更类型枚举。
   */
  public enum ChangeType {
    /** 新增列 */
    ADD_COLUMN,
    /** 删除列 */
    DROP_COLUMN,
    /** 类型变更 */
    TYPE_CHANGE,
    /** 长度变更 */
    LENGTH_CHANGE,
    /** 可空性变更 */
    NULLABLE_CHANGE
  }

  /**
   * 单个结构变更。
   */
  public static final class Change {
    /** 变更类型 */
    private final ChangeType type;
    /** 涉及的列名 */
    private final String columnName;
    /** 变更详情（如 "old -> new"） */
    private final String detail;

    /**
     * 构造变更项。
     *
     * @param type       变更类型
     * @param columnName 列名
     * @param detail     变更详情
     */
    public Change(ChangeType type, String columnName, String detail) {
      this.type = type;
      this.columnName = columnName;
      this.detail = detail;
    }

    /**
     * 获取变更类型。
     *
     * @return 变更类型
     */
    public ChangeType getType() {
      return type;
    }

    /**
     * 获取列名。
     *
     * @return 列名
     */
    public String getColumnName() {
      return columnName;
    }

    /**
     * 获取变更详情。
     *
     * @return 变更详情
     */
    public String getDetail() {
      return detail;
    }
  }

  /**
   * 计算新旧列结构的差异。
   * <p>
   * 先以列名为键建立映射，再遍历新列检测新增与属性变更，遍历旧列检测删除。
   *
   * @param oldColumns 旧列结构
   * @param newColumns 新列结构
   * @return 变更列表
   */
  public List<Change> diff(List<SchemaScanner.ColumnInfo> oldColumns,
      List<SchemaScanner.ColumnInfo> newColumns) {
    List<Change> result = new ArrayList<Change>();

    Map<String, SchemaScanner.ColumnInfo> oldMap = toMap(oldColumns);
    Map<String, SchemaScanner.ColumnInfo> newMap = toMap(newColumns);

    // 遍历新列，检测新增与属性变更
    for (SchemaScanner.ColumnInfo newCol : newColumns) {
      SchemaScanner.ColumnInfo oldCol = oldMap.get(newCol.getColumnName());
      if (oldCol == null) {
        // 旧结构中不存在该列，视为新增
        result.add(new Change(ChangeType.ADD_COLUMN, newCol.getColumnName(),
            "type=" + newCol.getDataType()));
      } else {
        // 检测类型变更
        if (!equalsStr(oldCol.getDataType(), newCol.getDataType())) {
          result.add(new Change(ChangeType.TYPE_CHANGE, newCol.getColumnName(),
              oldCol.getDataType() + " -> " + newCol.getDataType()));
        }
        // 检测长度变更
        if (!equalsObj(oldCol.getDataLength(), newCol.getDataLength())) {
          result.add(new Change(ChangeType.LENGTH_CHANGE, newCol.getColumnName(),
              oldCol.getDataLength() + " -> " + newCol.getDataLength()));
        }
        // 检测可空性变更
        if (oldCol.isNullable() != newCol.isNullable()) {
          result.add(new Change(ChangeType.NULLABLE_CHANGE, newCol.getColumnName(),
              oldCol.isNullable() + " -> " + newCol.isNullable()));
        }
      }
    }

    // 遍历旧列，检测删除
    for (SchemaScanner.ColumnInfo oldCol : oldColumns) {
      if (!newMap.containsKey(oldCol.getColumnName())) {
        // 新结构中不存在该列，视为删除
        result.add(new Change(ChangeType.DROP_COLUMN, oldCol.getColumnName(),
            "dropped"));
      }
    }

    return result;
  }

  /**
   * 将列信息列表按列名映射。
   *
   * @param columns 列信息列表
   * @return 列名 → 列信息
   */
  private Map<String, SchemaScanner.ColumnInfo> toMap(List<SchemaScanner.ColumnInfo> columns) {
    Map<String, SchemaScanner.ColumnInfo> result = new HashMap<String, SchemaScanner.ColumnInfo>();
    if (columns != null) {
      for (SchemaScanner.ColumnInfo col : columns) {
        result.put(col.getColumnName(), col);
      }
    }
    return result;
  }

  /**
   * 字符串相等比较（容忍 null）。
   *
   * @param a 字符串 a
   * @param b 字符串 b
   * @return 相等返回 true
   */
  private boolean equalsStr(String a, String b) {
    boolean result;
    if (a == null && b == null) {
      result = true;
    } else if (a == null || b == null) {
      result = false;
    } else {
      result = a.equals(b);
    }
    return result;
  }

  /**
   * 对象相等比较（容忍 null）。
   *
   * @param a 对象 a
   * @param b 对象 b
   * @return 相等返回 true
   */
  private boolean equalsObj(Object a, Object b) {
    boolean result;
    if (a == null && b == null) {
      result = true;
    } else if (a == null || b == null) {
      result = false;
    } else {
      result = a.equals(b);
    }
    return result;
  }
}
